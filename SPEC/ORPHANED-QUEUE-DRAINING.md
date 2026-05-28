# Orphaned DGS Queue Draining Specification

## Overview

When a core instance dies, the per-instance result-return queue it owned (`dgs-<uniqueId>`)
can still hold completed action results that were never processed. Today those orphaned
queues are **dropped** once their heartbeat goes stale. Dropping discards the completed
results, so the affected actions are re-executed by the requeue scheduler — which is wasteful
for idempotent actions and a correctness problem for egress (duplicate delivery to external
systems).

This spec defines draining orphaned queues through the normal result-processing path instead
of dropping them, so a completed result is *processed* rather than the action *re-run*.

### Goals
- Process completed results stranded on dead instances' queues instead of discarding them
- Eliminate (or sharply reduce) duplicate egress caused by requeue re-executing already-completed actions
- Reuse existing safety mechanisms (atomic pop, optimistic locking, queue-membership guard)
- No correctness risk if a still-alive instance is mis-detected as dead

### Non-Goals
- Replacing the per-instance return-address affinity for normal new work (kept as-is)
- Guaranteeing exactly-once egress in all timing scenarios (see Residual Risk)

---

## Architecture Context

### Result-return queues
- Actions are dispatched to per-action-class queues; each carries a `returnAddress`.
- When an action completes, the plugin/action posts the **result** back to a result-return queue:
  - `dgs` — shared queue, used when `returnAddress` is null. Drained by every core instance via
    `DeltaFilesService.processActionEvents(null)` (`ActionEventScheduler`).
  - `dgs-<uniqueId>` — per-instance queue, used when a specific instance dispatched the work
    (cache affinity for new work). Drained by that instance via `processActionEvents(uniqueId)`.
- Both are Valkey sorted sets; consumers use `bzpopmin` (`ValkeyKeyedBlockingQueue.take`).
- Queue name for an instance: `CoreEventQueue.DGS_QUEUE + "-" + identityService.getUniqueId()`
  (see `HeartbeatService`).

### Liveness / orphan detection
- Each live instance publishes a heartbeat into the `org.deltafi.action-queue.heartbeat` hash
  (`ValkeyKeyedBlockingQueue.setHeartbeat`).
- `ValkeyKeyedBlockingQueue.getOldDgsQueues()` returns `dgs-`-prefixed queues whose heartbeat is
  older than 5 minutes.
- `CoreEventQueue.removeOrphanedDgsQueues()` currently **drops** those queues
  (`drop` + `removeHeartbeats`).

### Interaction with requeue
- `DeltaFilesService.requeue()` re-dispatches IN_FLIGHT actions whose flow `modified` is older
  than `requeueDuration` (default 5 min). The DB still shows the action as `QUEUED` because the
  orphaned completion was never processed, so requeue will re-run it.
- After the "spread re-driven work" change, requeue/cold-to-warm dispatch with a null return
  address, so re-driven results land on the shared `dgs` queue. Orphaned **per-instance** queues
  still accumulate when an instance dies mid-flight.

---

## Problem

Dropping an orphaned `dgs-<dead>` queue discards completed results. The corresponding action is
still `QUEUED` in the DB, so `requeue()` re-dispatches it after `requeueDuration`:

- **Idempotent transforms:** wasted re-execution.
- **Egress / data sinks:** the file is delivered to the external system a second time — a data
  integrity problem.

Both the orphan-drop window and `requeueDuration` default to 5 minutes, so they race.

---

## Design

### Drain instead of drop
Replace the drop in `removeOrphanedDgsQueues()` with a drain: for each orphaned `dgs-<dead>`
queue, pop entries (`bzpopmin` / `take`) and feed each through the existing
`DeltaFilesService.processResult(event)` path, exactly as a normal result. After the queue is
empty, remove its heartbeat.

Why this is safe even if a "dead" instance is actually alive:
- Pops are atomic. A live instance and the drainer racing on the same queue simply split the
  items; each result is popped and processed exactly once. The only cost is a cache-affinity miss
  on stolen items — never double-processing.
- `handleActionEvent` already uses optimistic locking and checks action state, so a result for an
  action that has since been advanced elsewhere is rejected/ignored, not double-applied.

### Beating the requeue race
Processing a stranded result only *prevents* double egress (rather than merely stale-rejecting it)
if the drain runs **before** requeue re-dispatches the action. To achieve that:

1. **Drain window < `requeueDuration`.** Detect/drain orphans on a window comfortably shorter than
   the 5-minute requeue threshold (e.g. orphan-eligible after ~1–2 min stale heartbeat), and run
   the drain on a short fixed-delay schedule so late-arriving results are still caught before the
   requeue mark.
2. **Queue-membership guard on requeue (defense in depth).** Reuse the `exists()` check already
   used as `checkUnique` in `CoreEventQueue.putActions` — before requeue re-dispatches a did, skip
   it if a result for that flow is already pending in a queue. (This is the same guard requeue uses
   on the way out; the drain path benefits from it symmetrically.) This closes the bulk of the
   remaining window.

### Components
- **`CoreEventQueue`**: add `drainOrphanedDgsQueues(Consumer<ActionEvent>)` (or have it call back
  into result processing) that, for each orphaned queue, loops `take(queueName)` until empty and
  invokes the result handler; then removes the heartbeat. Replaces / augments
  `removeOrphanedDgsQueues()`.
- **Scheduler**: a short fixed-delay task (guarded by `schedule.actionEvents` / `schedule.maintenance`
  as appropriate) invoking the drain.
- **Config**: a property for the orphan-stale window (default below `requeueDuration`), and the
  drain interval.

---

## Residual Risk
Not airtight. If an action completes slowly and posts its result to `dgs-<dead>` only after the
requeue mark, requeue may have already re-dispatched it before the drainer sees it — yielding a
duplicate execution. The membership guard and a sufficiently tight drain window make this rare,
but egress that must be exactly-once should still be idempotent or deduplicated downstream.

---

## Testing
- **Drain processes results:** seed a `dgs-<dead>` sorted set with a completed result, run the
  drain, assert the DeltaFile advances (result processed) and the queue + heartbeat are removed.
- **No double-processing under contention:** two drainers (or a live consumer + drainer) racing the
  same queue process each entry exactly once.
- **Stale result rejected:** a result for an action already advanced elsewhere is ignored
  (optimistic-lock / state-check path), no double-advance.
- **Race window:** with the membership guard, a did with a pending queued result is not
  re-dispatched by requeue.
- **No egress duplication (integration):** kill an instance after an egress action completes but
  before its result is processed; verify the drain processes the completion and requeue does not
  re-run the egress.

---

## Status
Proposed. Depends on the "spread re-driven work + result-queue backpressure" change
(requeue/cold-to-warm dispatch with null return address; `requeueBacklogThreshold` brake on the
shared `dgs` queue depth), which is the prerequisite for keeping live per-instance queues small.
