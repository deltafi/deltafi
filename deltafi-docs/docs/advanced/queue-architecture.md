# Queue Architecture

DeltaFi uses a two-tier queuing system to handle backpressure while ensuring no data is lost. Actions are queued in either a **warm queue** (Valkey, in-memory) for fast dispatch or a **cold queue** (PostgreSQL, on-disk) when the warm queue reaches capacity.

## Overview

```
                                    ┌─────────────────────┐
                                    │   Action Workers    │
                                    │  (consume & process)│
                                    └──────────▲──────────┘
                                               │
                    ┌──────────────────────────┴──────────────────────────┐
                    │                                                      │
            ┌───────┴───────┐                                    ┌────────┴────────┐
            │  Warm Queue   │◄───── coldToWarm() ────────────────│   Cold Queue    │
            │   (Valkey)    │       (every 5s when space)        │  (PostgreSQL)   │
            └───────▲───────┘                                    └────────▲────────┘
                    │                                                      │
                    │ queue size < max                    queue size >= max│
                    │                                                      │
                    └──────────────────────┬───────────────────────────────┘
                                           │
                                   ┌───────┴───────┐
                                   │  StateMachine │
                                   │  queueState() │
                                   └───────────────┘
```

## Why Two Tiers?

**Problem:** Under heavy load, action workers may not keep up with incoming data. Without backpressure handling, the in-memory queue would grow unbounded and eventually cause out-of-memory failures.

**Solution:** When the warm queue reaches its configured maximum size, new items overflow to the cold queue in PostgreSQL. This provides:

- **Bounded memory usage** - Warm queue has a fixed maximum size
- **No data loss** - Overflow items persist to disk
- **Automatic recovery** - Items warm up automatically when capacity frees

## Warm Queue (Valkey)

The warm queue uses Valkey (Redis-compatible) sorted sets for fast, in-memory queuing.

### Key Structure

- **Key name:** Action class name (e.g., `org.deltafi.core.action.MyTransform`)
- **Data structure:** Sorted set (ZSET)
- **Score:** Timestamp (epoch milliseconds) - ensures FIFO ordering
- **Value:** JSON-serialized `WrappedActionInput`

Each action class has its own independent queue. The maximum size is configured **per action class**, not system wide.

### Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `inMemoryQueueSize` | 5000 | Maximum items per action class in warm queue |

When any action class queue reaches `inMemoryQueueSize`, that specific action goes into cold queue mode.

### Queue Item Contents

Each queued item (`WrappedActionInput`) contains:

```java
ActionInput {
    ActionContext actionContext {
        UUID did;           // DeltaFile ID
        String flowName;    // Flow definition name
        String actionName;  // Action name within flow
        String dataSource;  // Data source name
        // ... other context
    }
    String queueName;       // Action class name
    // ... parameters, content references
}
```

## Cold Queue (PostgreSQL)

When the warm queue overflows, items are marked with `ActionState.COLD_QUEUED` and persisted to PostgreSQL.

### Storage

Cold queue state is stored in the `delta_file_flows` table:

| Column | Type | Description |
|--------|------|-------------|
| `cold_queued` | BOOLEAN | Whether flow is cold queued |
| `cold_queued_action` | TEXT | Action class name (for query efficiency) |

The actual DeltaFile and flow data is already in PostgreSQL; the cold queue columns simply mark which items are waiting for warm queue space.

### Indexes

```sql
-- Efficient lookup of cold queued items by action class
CREATE INDEX idx_delta_file_flows_cold_queued
ON delta_file_flows (cold_queued_action, delta_file_id)
WHERE state = 'IN_FLIGHT' AND cold_queued_action IS NOT NULL;
```

## State Transitions

### Warm to Cold (Overflow)

Triggered in `StateMachine.queueState()` when:
1. New action needs to be queued
2. `QueueManagementService.coldQueue(queueName, pendingCount)` returns true
3. Condition: `currentQueueSize + pendingCount >= inMemoryQueueSize`

The action state is set to `COLD_QUEUED` instead of `QUEUED`, and when the DeltaFile is saved, `cold_queued_action` is populated.

### Cold to Warm (Recovery)

Triggered by `QueueManagementService.coldToWarm()` every 5 seconds:

1. Get list of action classes with cold queued items
2. For each action class:
   - Check warm queue size
   - If size < 90% of max, fetch oldest cold items
   - Call `DeltaFilesService.requeueColdQueueActions()`
   - Items move from cold queue to warm queue

### Thrash Prevention

To prevent rapid cycling between warm and cold states, the system uses hysteresis:

- **Go cold:** When warm queue reaches 100% of `inMemoryQueueSize`
- **Stay cold:** Until warm queue drops to 80% AND all cold items are drained
- **Warm up:** Only when there's headroom (queue < 90% capacity)

This prevents oscillation when the system is near capacity.

## Key Files

| File | Responsibility |
|------|----------------|
| `QueueManagementService.java` | Orchestrates warm/cold transitions, tracks queue sizes |
| `StateMachine.java` | Decides `QUEUED` vs `COLD_QUEUED` at queue time |
| `DeltaFilesService.java` | `requeueColdQueueActions()` moves items warm |
| `DeltaFileFlow.java` | State tracking, sets `coldQueuedAction` column |
| `CoreEventQueue.java` | Valkey queue operations (put, take, size) |
| `ValkeyKeyedBlockingQueue.java` | Low-level Valkey operations |
| `ColdQueueCheck.java` | Health monitoring, counts cold items |
| `DeltaFileFlowRepo.java` | Database queries for cold queue |

## Monitoring

### Queue Size Tracking

`QueueManagementService` maintains in-memory tracking:
- `allQueues`: `ConcurrentHashMap<String, Long>` - size per action class
- `coldQueues`: `ConcurrentSkipListSet<String>` - which actions are in cold mode
- Refreshed every 2 seconds from Valkey

### Health Checks

- **`ActionQueueCheck`** - Publishes warm queue sizes as metrics
- **`ColdQueueCheck`** - Monitors cold queue depth, caches total count in Valkey

### Metrics

| Metric | Source | Description |
|--------|--------|-------------|
| `gauge.action_queue.queue_size` | ActionQueueCheck | Per-action warm queue size |
| `gauge.deltafile.coldQueuedCount` | ColdQueueCheck | Total cold queued count |
| `gauge.deltafile.warmQueuedCount` | DeltaFilesService | Total warm queued count |

## Scaling Considerations

### When to Increase `inMemoryQueueSize`

- Workers are keeping up but queue fluctuates near max
- You have memory headroom on Valkey
- You want to reduce cold queue churn

### When to Add More Workers

- Cold queue grows continuously
- Warm queue stays at max despite workers running
- Processing latency is acceptable but throughput isn't

### When Cold Queue Grows Unbounded

This indicates workers can't keep up with ingest rate. Options:
1. Scale out action workers (more pods/threads)
2. Optimize slow actions
3. Rate limit at ingress
4. Temporarily pause affected data sources

## Per-Flow Queue Metrics

While queues are keyed by action class, detailed metrics can be obtained at the (actionClass, flowName, actionName) granularity.

### Warm Queue Per-Flow Metrics

`QueueManagementService.getDetailedWarmQueueMetrics()` scans Valkey queues and aggregates by flow:

```java
public record WarmQueueMetrics(
    String actionClass,
    String flowName,
    String actionName,
    int count,
    OffsetDateTime oldestQueuedAt) {}
```

Results are cached for 5 seconds to avoid excessive scanning.

### Implementation Details

The warm queue scan works by:
1. Iterating over all known action class queues
2. Using `ZSCAN` cursor-based iteration to stream through items without loading all into memory
3. Parsing JSON to extract `flowName` and `actionName` from `ActionContext`
4. Aggregating counts and tracking oldest timestamp per (actionClass, flowName, actionName)

### Cold Queue Per-Flow Metrics

Cold queue items are tracked in the `cold_queue_entries` table, which stores one row per cold-queued item:

```sql
CREATE TABLE cold_queue_entries (
    delta_file_flow_id UUID PRIMARY KEY,
    delta_file_id UUID NOT NULL,
    flow_name TEXT NOT NULL,
    flow_type TEXT,
    action_name TEXT NOT NULL,
    action_class TEXT NOT NULL,
    queued_at TIMESTAMPTZ NOT NULL
);
```

Triggers on `delta_file_flows` automatically insert/delete entries when items enter/leave the cold queue. This provides:
- **Accurate counts** via `COUNT(*)`
- **Oldest timestamp** via `MIN(queued_at)`
- **DID references** for drill-down (future enhancement)

`DeltaFileFlowRepo.getColdQueueCounts()` aggregates from this table:

```java
public record ColdQueueMetrics(String flowName, String flowType, String actionName,
                               String actionClass, int count, OffsetDateTime oldestQueuedAt) {}
```

### REST API

`GET /api/v2/metrics/queues/detailed` returns both warm and cold queue metrics:

```json
{
  "warmQueues": [
    {
      "actionClass": "org.example.MyTransform",
      "flowName": "my-flow",
      "flowType": "TRANSFORM",
      "actionName": "transform-step",
      "count": 150,
      "oldestQueuedAt": "2024-12-10T10:00:00Z"
    }
  ],
  "coldQueues": [
    {
      "flowName": "slow-flow",
      "flowType": "DATA_SINK",
      "actionName": "slow-step",
      "actionClass": "org.example.SlowAction",
      "count": 12500,
      "oldestQueuedAt": "2024-12-10T09:15:00Z"
    }
  ],
  "timestamp": "2024-12-10T10:05:00Z"
}
```

`GET /api/v2/metrics/queues/action-plugins` returns a mapping of action classes to plugin artifact IDs:

```json
{
  "org.example.MyTransform": "my-plugin:1.0.0",
  "org.deltafi.core.action.FilterAction": "deltafi-core-actions:2.43.0"
}
```

`GET /api/v2/metrics/queues/running` returns currently executing tasks:

```json
{
  "tasks": [
    {
      "actionClass": "org.example.MyTransform",
      "actionName": "transform-step",
      "did": "abc-123-def",
      "startTime": "2024-12-10T10:00:00Z",
      "runningForMs": 330000,
      "appName": "deltafi-core-worker-1"
    }
  ],
  "heartbeatThresholdMs": 30000
}
```

## Queue Metrics Page

The DeltaFi UI provides a Queue Metrics page (`/queue-metrics`) for monitoring queue status.

### Features

**Action Queues Table:**
- Per-flow breakdown showing Flow, Type, Action, and Action Class columns
- Warm and Cold queue counts per action
- Total count and "Oldest" timestamp showing how long items have been waiting
- Hover over action class to see which plugin provides it
- Toggle "Group by Action Class" to aggregate metrics across flows

**Running Tasks Panel:**
- Collapsible panel (collapsed by default) showing currently executing actions
- Links to DeltaFile viewer via DID
- Shows duration, started time, and worker pod name
- Highlights tasks running longer than 5 minutes in warning color
- Auto-refreshes every 5 seconds

### Use Cases

1. **Identify bottlenecks:** High cold queue counts indicate an action can't keep up
2. **Spot stale items:** The "Oldest" timestamp shows if items are stuck waiting
3. **Monitor load distribution:** Per-flow breakdown shows which flows are generating load
4. **Debug stuck actions:** Running Tasks panel shows what's currently executing

## Gotchas

1. **Queue key is action class, not flow** - Multiple flows using the same action class share a queue. A slow flow can impact others using the same action.

2. **Cold queue table grows with queue depth** - The `cold_queue_entries` table has one row per cold-queued item. With millions of cold-queued items, the table can become large. However, queries are efficient due to purpose-built indexes.

3. **Warm queue data lost on Valkey restart** - Items in the warm queue are not persisted. On Valkey restart, in-flight items may need requeue (handled by Core's requeue mechanism).
