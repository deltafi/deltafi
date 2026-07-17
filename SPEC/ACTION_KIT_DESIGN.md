# DeltaFi Action Kit — Language-Agnostic Design

This document specifies everything required to implement a DeltaFi action kit in a new
programming language. It is the primary guideline for future language ports. It is derived
from the four reference implementations — Java, Python, Go, and C++ — and describes the
**contracts** every kit must satisfy, independent of any one language's features.

The guiding principle: **a kit is a long-running worker process that registers its actions
with DeltaFi core, pulls work events off a Valkey/Redis queue, executes user-written actions,
and publishes result events back onto the queue.** Everything else is detail in service of
that loop.

> **Three cross-cutting mandates** (called out throughout, restated in §16):
> 1. **Streaming is first-class** for both content and artifacts — bounded memory regardless
>    of object size.
> 2. **A unit-test framework ships with the kit** — in-memory backends, runners that exercise
>    the real production surface, and result assertions. No mocks in the data path.
> 3. **Use the target language's idioms and best practices** for every concept here. The
>    contracts are fixed; the expression of them must be native to the language.

---

## 0. Conformance Levels

A kit is **wire-conformant** if it can register with core and exchange events such that core
cannot distinguish it from the reference kits. This requires, at minimum:

- The queue protocol (§8): BZPOPMIN intake, ZADD-by-timestamp publish, queue naming.
- The JSON event schemas (§3, §8): exact field names (camelCase), null omission, time formats.
- The content object-storage layout (§5): the `<did[:3]>/<did>/<uuid>` object key scheme.
- The registration payload (§7): `POST {CORE_URL}/plugins`.

Everything beyond that (lookup tables, artifacts, the full set of result helpers) is additive
but expected for a complete kit. The mandatory subset is summarized in §16.

---

## 1. Process Model & Lifecycle

A kit is a single deployable process (typically a container). Its lifecycle:

1. **Resolve plugin identity** — coordinates `(groupId, artifactId, version)`, display name,
   description, action-kit version, dependencies. Sourced from build-time metadata and/or
   environment variables (§13). Use whatever identity mechanism is idiomatic (build info file,
   link-time variables, env vars).
2. **Discover/collect actions** — gather every action the plugin provides, plus lookup-table
   suppliers and artifact watches. Discovery may be explicit (a registration list) or
   automatic (annotation/decorator/macro/reflection scan) — choose what is idiomatic.
3. **Connect dependencies** — Valkey/Redis (queue), S3/MinIO (content storage). Establish
   HTTP client(s) for core (registration, GraphQL, REST). Configure TLS if certs are present.
4. **Register with core** — build and POST the plugin registration (§7). Registration must
   succeed (with retry) before the process begins consuming work.
5. **Load flows, variables, integration tests** — from packaged resources (e.g. `flows/`,
   `integration/` directories); pass them through to registration mostly verbatim.
6. **Start side-services** — lookup-table supplier runner, artifact version-change listener,
   cache evictors.
7. **Start workers** — one worker group per action (§2).
8. **Start heartbeat** — periodic liveness reporting (§8).
9. **Signal readiness** — create the health-probe marker file `/tmp/running` (used by
   container orchestration liveness/readiness probes).
10. **Run until signaled** — on SIGTERM/SIGINT, stop accepting work, **drain in-flight workers
    with a 30-second deadline**, remove the health-probe file, close connections, exit.

A kit must be **self-healing**: a failure executing one event must never crash the process.
Every outcome — success, business error, filter, or unexpected exception/panic — maps to a
result event (§3, §11).

### 1.1 Health probe (distroless-friendly)

> **Status: TARGET DESIGN — not yet implemented in any kit.** All kits currently signal
> readiness with the file marker `/tmp/running`, probed via `exec: ["cat", "/tmp/running"]`
> (k8s) / `test: cat /tmp/running` (compose). This subsection defines the direction that
> replaces it. See the conformance matrix (Lifecycle row) for per-kit status.

**Problem.** The `cat /tmp/running` exec probe forces a shell/coreutils binary into every
plugin image purely to read a sentinel file. That is the single thing blocking **distroless**
plugin images: a distroless container has no `cat`, so the probe can never pass.

**Requirement.** Each kit exposes a minimal **HTTP health endpoint** on a fixed health port,
and orchestration uses a native `httpGet` probe. The kubelet performs the GET itself, so
**nothing needs to exist inside the container** — distroless works unchanged. This also
supersedes the file marker: a `503` carries real meaning that a file's existence cannot.

The handler must reflect **real internal state**, not merely "a thread is listening" — an
endpoint that returns a hardcoded `200` is no better than the file marker it replaces. For a
queue-pull plugin, real state means: registration complete, worker groups started, heartbeat
fresh, and the Valkey queue connection live.

**Split the checks by probe type** — this is the sharp edge:

- **Startup** — `200` once the kit has registered, started its worker groups, and begun
  heartbeating (the signal the `/tmp/running` marker gives today). Everything else waits on it.
- **Liveness** — **dumb on purpose**: check only that *this process* is responsive (event loop
  not wedged / not deadlocked). It **MUST NOT** check external dependencies (Valkey, MinIO,
  core). A liveness handler that pings Valkey turns a 30-second Valkey blip into a `503` on
  every plugin at once, and the kubelet restarts the **entire fleet** simultaneously — a
  self-inflicted outage. Keep external deps out of liveness.
- **Readiness** — reflects real state: heartbeat fresh **and** queue connection live; `503`
  when degraded. Readiness `503` removes the pod from service and marks it not-ready but does
  **not** restart it, so it recovers on its own when the dependency returns. This is where the
  "`503` when things go south" behavior belongs.

**Why not `tcpSocket`.** A `tcpSocket` probe only reports *connect succeeded / refused* — there
are no status codes, so it cannot express "up but degraded" except by tearing the listener
down, which abuses socket lifecycle as a boolean. The moment graded health is wanted, the
mechanism is HTTP.

**Why not exec the plugin's own binary.** `exec: ["/plugin", "healthcheck"]` avoids `cat` and
suits a static Go binary, but for Java/Python it spins a JVM/interpreter on every probe
interval — expensive and non-uniform across kits. Reject it.

**Compose parity (open).** Docker's `HEALTHCHECK` cannot do an `httpGet` natively without a
client binary in the image (`curl`/`wget`), which reintroduces exactly the dependency we are
removing. So compose either keeps the file marker for its healthcheck or diverges from k8s.
Decide whether the two orchestrators share the mechanism before implementing.

**Open questions to settle before implementation:**
1. Which probes to wire — replace only startup, or add the dumb-liveness / dependency-aware
   readiness pair while here (the `httpGet` design makes the latter cheap).
2. Health port — a fixed port across all plugins (so the chart can hardcode it) vs configurable
   via `values.yaml`; must not collide with a port a plugin author might bind.
3. For a pure queue puller there is **no inbound Service**, so readiness gates no traffic — its
   only value is if core/UI consumes pod readiness to report plugin health. If nothing reads
   it, ship startup + dumb liveness and skip readiness.
4. Whether `/tmp/running` stays as an interim mechanism during migration or is removed outright.

---

## 2. Worker / Concurrency Model

Each registered action gets an independent worker group. The reference model (Go/C++/Java all
converge here) is:

- **One poller** performing a blocking pop (`BZPOPMIN`) on the action's input queue.
- **N worker units** that execute events concurrently. N is the *worker count* for that action.
- The poller hands raw messages to workers via whatever concurrency primitive is idiomatic
  (bounded channel + goroutines, thread pool, thread-per-worker, async tasks, GIL-bound
  threads). The reference kits use: Go — buffered channel + goroutines; C++ — a thread pool;
  Java — one polling thread per worker instance; Python — N threads per action.

**Worker count resolution** (highest precedence first), per action:
1. Explicit programmatic override.
2. Per-action env var `ACTIONS_ACTIONTHREADS_{NAME}` (name uppercased, `.`/`-` → `_`).
3. Global env var `ACTION_THREADS`.
4. Default **1**.

**Per-execution responsibilities of a worker** (the inner loop):
1. Blocking-pop one raw JSON message from the action's input queue.
2. Deserialize into the input event (§8).
3. Build the typed `ActionContext` and typed input (§4), wiring runtime fields
   (action version, hostname, start time, the content-storage handle).
4. Bind parameters: raw param map → typed parameters (§6), including string coercion.
5. Record the execution in the in-flight tracker (for long-running-task reporting, §8).
6. **Invoke the action's single method** inside a fault barrier (catch all exceptions/panics).
7. Map the outcome (or thrown disposition/uncaught error) to a result event (§3, §11).
8. **Orphan-content GC** (§5): delete any content saved during execution that the result event
   does not reference.
9. Clear the in-flight record.
10. Publish the result event (§8).

**Fault isolation barriers** must wrap, at minimum: action dispatch, lookup-supplier callbacks,
and artifact version-change callbacks. A failure in any of these is logged/converted, never
fatal to the process.

---

## 3. Action Types & Result Types

### 3.1 Action types

The action-type identifiers (wire strings, used in both registration and result events):

| Type | Identifier | Responsibility | Input | Allowed results |
|---|---|---|---|---|
| Transform | `TRANSFORM` | Modify content/metadata; pass through; annotate; or split into N children. | content list + metadata | Transform (1), Transforms (N children), Error, Filter |
| Egress | `EGRESS` | Send one content item to an external system. Terminal. | single content + metadata (first content item) | Egress (no payload), Error, Filter |
| Timed Ingress | `TIMED_INGRESS` | Data **source**: periodically fetch external data and emit new DeltaFiles. | none (reads `memo` for cross-run state) | Ingress (items + memo + status), Error, Filter |

> The enum also reserves `INGRESS`, `PUBLISH`, and `UNKNOWN`, but **no kit ships action base
> classes for them** — they are core-side concepts. A new kit only implements the three above.

**Join** is **not** a separate action type. It is a Transform that additionally provides a
`join` operation. When an incoming event carries a join configuration (`actionContext.join` is
present), the runtime:
1. Builds one transform-input per joined DeltaFile message.
2. Calls the action's `join(inputs) → mergedInput` (default behavior: concatenate all content
   lists and merge all metadata maps, later-wins on key collisions).
3. Runs the normal `transform` once on the merged input.

Registration reports `supportsJoin: true` for such actions. The post-join → transform handoff
is subtle (only the merged metadata and merged output content flow into the transform input);
implement and test it carefully.

### 3.2 The action contract

Every action exposes:
- An **identity**: a name (the reference kits derive it from the class/type name, qualified
  with the plugin group/domain → `group.TypeName` or `domain.name`), an action type, and a
  human description.
- A **parameter type** (may be empty) — see §6.
- **Optional rich documentation** (`ActionOptions`) for the UI — see §7.
- **Exactly one method** the implementer writes, by type:
  - `transform(context, params, input) → TransformResult | TransformResults | Error | Filter`
  - `egress(context, params, input) → Egress | Error | Filter`
  - `ingress(context, params) → Ingress | Error | Filter`

Express "one of these result types" with whatever idiomatic sum-type the language offers
(sealed hierarchy, tagged union, `variant`, marker interface, multiple concrete return classes).
Express the method contract idiomatically (interface, abstract method, concept, decorated
function). Parameter-vs-method ordering and optional-params overloads are language conveniences,
not part of the contract.

### 3.3 Result types

All results carry, in common: the action context, a list of structured **log messages** (§11),
and a list of **metrics** (§11). Concrete types:

| Result | Event type | Payload semantics |
|---|---|---|
| **Transform** | `TRANSFORM` | One continuation: `content[]`, `metadata` (additions), `deleteMetadataKeys[]`, `annotations`. |
| **Transforms** (children) | `TRANSFORM` | N children; each child has a fresh DID (split/fan-out), its own content/metadata/annotations/messages, and an optional child DeltaFile `name`. |
| **Egress** | `EGRESS` | Header only; no payload (egress is terminal). |
| **Ingress** | `INGRESS` | `memo` (opaque cross-run state), `executeImmediate` (bool), `status` (HEALTHY/DEGRADED/UNHEALTHY), `statusMessage`, and `ingressItems[]` — each item is a new DeltaFile with a fresh DID, content, metadata, annotations, name. |
| **Error** | `ERROR` | `cause` (summary), `context` (detail, includes stack trace if from an exception), `annotations`. |
| **Filter** | `FILTER` | `message`, optional `context`, `annotations`. Intentional, **non-error** drop. |

**Error vs Filter vs Success** is conveyed solely by the event `type` field and which payload
sub-object is populated. A **Filter is not a failure** — it is the action deciding the DeltaFile
should be dropped/skipped by design.

**Result builder helpers** every kit should provide on the relevant result types:
- Save content (string / bytes / **reader-stream** / writer-callback) → returns a content
  reference and appends it. Streaming variants are mandatory (§5).
- Add/replace metadata, delete metadata keys (transform), add annotations.
- Add a metric; log info/warning/error (attach a structured message).

### 3.4 Errors and filters as thrown control flow

In addition to *returning* an Error/Filter result, the kit should let actions **throw** a
disposition (where idiomatic) that the worker converts to the corresponding result — e.g. an
"error result exception" carrying cause/context/annotations/metrics, and a "filter result
exception". The reference input accessors throw domain exceptions (§4) that map to clean error
results. Use the language's natural error-signaling mechanism; in languages where returning is
idiomatic (Go), returning error state on the working object is the norm instead.

---

## 4. Input & Domain Model

Actions do **not** receive a whole DeltaFile or its flow history. Core projects the relevant
slice into per-message structures.

### 4.1 ActionContext

Per-invocation execution context. Fields (camelCase on the wire):
`did`, `deltaFileName`, `dataSource`, `flowName`, `flowId`, `actionName`, `actionVersion`,
`hostname`, `systemName`, `startTime`, `memo`, plus join data (`join`, `joinedDids`).

At runtime the worker also attaches (not from the wire): the content-storage handle, lookup
client, artifact client, and a list/accumulator of segments saved during this execution (for
orphan GC, §5). `childContext()` produces a copy with a **freshly generated UUID DID**, used to
spawn child DeltaFiles (splits and ingress items).

`JoinConfiguration` carries: `maxAge` (seconds), `minNum`, `maxNum`, `metadataKey`.

### 4.2 Per-message input

The wire delivers one or more **DeltaFile messages**, each `{ metadata, contentList }`. The kit
adapts these into typed inputs:
- **Transform input**: `content[]` + `metadata`. Accessors: `hasContent()`, `firstContent()`,
  `contentAt(i)` (throws "expected content" if out of range), `contentNamed(name)`,
  `getMetadata(key)` (throws "missing metadata" if absent), `getMetadataOrDefault(key, default)`.
- **Egress input**: a single `content` (the first item, or null) + `metadata`.
- **Ingress input**: empty (timed ingress receives no DeltaFile).

### 4.3 Metadata vs Annotations

- **Metadata** — string→string key/values carried with the DeltaFile; actions read them on
  input and add/delete them on output.
- **Annotations** — searchable string→string key/values indexed by core; **write-only** outputs
  attached to results (transform, ingress items, error, filter).

---

## 5. Content & Object Storage  *(STREAMING IS MANDATORY)*

Content bytes live in S3/MinIO-compatible object storage. The kit never ships bytes through the
queue — only references.

### 5.1 Segments and the object-key layout

A content blob is a list of **segments**. Each segment:
`{ uuid, offset (int64), size (int64), did }`.

The S3 object key for a segment is **exactly**:

```
<first 3 chars of did> / <did> / <uuid>
```

The 3-char prefix is a sharding hint for bucket fan-out. **This layout is part of the wire
contract** — a kit must reproduce it precisely to interoperate with core and the other kits.
Total content size = sum of segment sizes. Segments allow one logical content to span multiple
stored objects.

### 5.2 Content reference object

The author-facing content type wraps `{ name, mediaType, segments[], tags[] }` plus a (transient,
non-serialized) handle to the storage service so it can lazily load. It must support:
- **Lazy load**: `loadBytes()`, `loadString()`, and **`loadReader()` / `loadStream()`** (a
  streaming reader). Loading multi-segment content streams the segments in sequence.
- **Zero-copy slicing & composition** (no I/O): `subcontent(offset, size)` slices across segment
  boundaries by adjusting segment offset/size; `append`/`prepend` splice segment lists; `copy`.
  This is how large content is rearranged without reading bytes.
- Size, name/mediaType accessors, tag operations.

### 5.3 Storage service interface

Abstract over object storage:
- `save(did, bytes) → segment`
- **`saveFromReader(did, reader[, size]) → segment`** — streaming upload; size may be unknown up
  front and is discovered as bytes stream.
- `load(segments) → bytes`
- **`loadReader(segments) → stream`** — streaming read.
- `delete(segments)`

### 5.4 Streaming requirements (first-class, not optional)

A conformant kit **must** provide bounded-memory streaming on the content path:

- **Uploads** stream in fixed-size parts (the reference uses **5 MiB** multipart parts — the S3
  minimum). Peak memory ≈ (part concurrency × part size), independent of object size.
- **Unknown size** uploads are supported: the recorded segment size is the number of bytes
  actually streamed (use a counting reader), never a guess.
- **Reads** open at most one object body at a time and use **server-side HTTP Range requests**
  (`Range: bytes=offset-(offset+size-1)`) per segment, so a partial read fetches only the bytes
  needed.
- A **writer-callback save** (the action is handed a writer/sink it streams into, piped to
  storage) is provided for producer-style code.

> **Reference status / target:** The Go kit is fully streaming-first (5 MiB multipart, lazy
> single-body Range reader, true-streamed-size). Java streams reader/writer saves and
> sequence-streams multi-segment loads. **Python and C++ currently buffer the content path in
> memory** (C++ sends per-segment `Range` headers but then buffers the whole body, and its
> SigV4-over-httplib write path buffers; Python ranges reads but joins all bytes in memory). A new
> implementation should match the **Go streaming-first design**, not the buffered shortcut.
> Treat full streaming as the target contract.

### 5.5 Orphan-content garbage collection

The worker tracks every segment saved during an execution. After the action returns, it diffs
the saved segments against the segments actually referenced by the emitted event (transform and
ingress content). **Any saved-but-unreferenced segment is deleted from storage.** This prevents
leaks when an action writes content and then returns an Error/Filter, or writes scratch content
it does not attach. Implement this; it is a correctness behavior, not an optimization.

---

## 6. Parameters & Schema

Each action declares a (possibly empty) **parameter type**. Use the idiomatic typed-config
mechanism: a class with annotated fields (Java), a pydantic model (Python), a struct with tags
(Go), a macro-declared struct (C++).

**Binding**: the incoming `actionParams` arrives as a map/object. The kit deserializes it into
the typed parameters. **Crucially, core may deliver all parameter values as strings** after
variable substitution — the kit **must coerce** strings to the declared field types
(bool/int/float/nested) before/while binding. This is mandatory for correct operation.

**JSON Schema generation**: at registration the kit publishes a JSON Schema for each action's
parameter type. Generate it however is idiomatic (reflection + a schema library, model
introspection, or macro-generated). Requirements:
- camelCase property names.
- `additionalProperties: false` on objects.
- Honor field-level `description`, `default`, `enum`, `required`, `minimum`/`maximum`.
- Inline any `$ref`/`$defs` if your generator emits them (core expects a self-contained schema).

**Special parameter types** every kit should provide:
- **`EnvVar`** — a secret-indirection type. It serializes as just the **name** of an environment
  variable (validated `^[A-Z][A-Z0-9_]*$`), tagged in schema with `x-deltafi-type: EnvVar`. The
  **secret value is never stored or transmitted** through config or the queue; the worker
  resolves it from the environment at runtime (`resolve()` / `resolveOrDefault()` / `isSet()`).
- **`DataSize`** — parses human strings like `"10MB"` (units B/KB/MB/GB/TB, 1024-based) to a byte
  count. Schema pattern `^[0-9]+(B|KB|MB|GB|TB)$`, tagged `x-deltafi-type: DataSize`.

---

## 7. Plugin Registration

At startup the kit builds a **plugin registration** and POSTs it to `{CORE_URL}/plugins` as JSON.

**Registration payload** (camelCase, omit nulls):
```
{
  image, imagePullSecret,
  pluginCoordinates: { groupId, artifactId, version },
  displayName, description,
  actionKitVersion,
  dependencies: [ { groupId, artifactId, version } ],
  actions: [ ActionDescriptor ],
  variables: [ Variable ],
  flowPlans: [ <raw flow plan JSON, passed through> ],
  lookupTables: [ LookupTableDefinition ],
  integrationTests: [ <raw integration test JSON, passed through> ]
}
```

**ActionDescriptor** (per action):
```
{ name, type, supportsJoin, schema: <JSON Schema>, actionOptions: <optional>,
  docsMarkdown: <optional>, description: <optional, deprecated> }
```
(All four reference kits still emit a deprecated `description` field alongside `actionOptions`.)

**Variable** (flow parameterization):
`{ name, description, dataType, required, defaultValue, value, masked }`.

**ActionOptions** (rich self-documentation surfaced in the UI; all optional):
`description`, `inputSpec`, `outputSpec`, `filters[]`, `errors[]`, `notes[]`, `details`. Specs
describe content (`contentSpec{name, mediaType, description}`), metadata, annotations, and a
`passthrough` flag; filters/errors may be plain strings or "description with conditions".

**Flows / variables / integration tests** are loaded from packaged resources (reference: a
`flows/` directory of JSON/YAML, with `variables.{json,yaml,yml}` pulled out as variables, and an
`integration/` directory) and passed through to registration. Flow plans and integration tests
are opaque to the kit — preserve and forward their raw JSON.

**Transport & retry**: POST JSON to `{CORE_URL}/plugins`. Retry with backoff (reference: ~4
attempts, backoffs around 500ms / 2s / 5s, 30s per-attempt timeout). **Fail fast on 4xx**
(client error — registration is malformed); retry 5xx/IO. Registration must succeed before
workers begin consuming.

---

## 8. Event Transport / Queue Protocol

**Backend**: Valkey (Redis-compatible). Use the idiomatic Redis client, or hand-roll RESP if no
suitable client exists (C++ does this over Asio) — but the **observable protocol is fixed**:

- **Queues are sorted sets (ZSETs).** The score is an epoch-millisecond timestamp, giving
  FIFO-by-time ordering.
- **Intake**: `BZPOPMIN` (blocking pop of lowest score) on the queue **named after the action**
  (the qualified action name). Use a ~5s server-side timeout and loop, so shutdown is responsive.
- **Publish**: `ZADD` with score = current epoch millis. `NX` semantics matter only for **input
  enqueues** (so an identical member isn't overwritten); result/reply publishes use unique members,
  so plain `ZADD` is fine there.
- **Result queue naming**: publish results to `dgs`, or to **`dgs-{returnAddress}`** when the
  incoming event carried a non-empty `returnAddress` (routes the result to the originating core
  instance).

**Poll-loop error backoff (resilience).** Every queue poll loop — the action intake loop above
and the lookup-supplier intake loop (§9.2) — must distinguish a *healthy* read (an event, or an
idle timeout with no work) from a *read error* (Valkey unreachable, connection refused, pool
exhausted). A healthy read proceeds normally; the blocking pop parks server-side while idle, so
an idle system never busy-polls. On a **read error** the loop **MUST NOT** retry tightly — it
applies **capped exponential backoff with jitter** before the next attempt:
- base **100 ms**, doubling on each consecutive error, capped at **5 s**;
- **jitter** each delay into `[delay/2, delay]` so many action threads do not reconnect in
  lockstep (thundering herd);
- **reset to base** after any healthy read;
- the wait **MUST be cancellable by shutdown** so SIGTERM/SIGINT still drains promptly.

Rationale: without this, a downed Valkey turns every poll loop into a CPU-pegging retry storm
that also churns TCP sockets fast enough to exhaust kernel socket-buffer memory
(`net.ipv4.tcp_mem`), hanging the node until restart. The reference values (100 ms → 5 s, half-to-full
jitter) are shared by the Java, Go, and Python kits.

**Incoming event** (`ActionInput`, JSON):
```
{
  deltaFileMessages: [ { metadata, contentList } ],
  actionContext: { did, deltaFileName, dataSource, flowName, flowId,
                   actionName, actionVersion, hostname, systemName, memo,
                   join, joinedDids },
  actionParams: { ... },          // raw, possibly all-strings
  queueName,
  returnAddress
}
```

**Outgoing event** (`ActionEvent`, JSON):
```
{
  did, flowName, flowId, actionName,
  start, stop,                    // timestamps (see serialization rules)
  type,                           // TRANSFORM | EGRESS | INGRESS | ERROR | FILTER
  transform: [ TransformEvent ],  // exactly one payload populated to match `type`
  error:   { cause, context, annotations },
  filter:  { message, context, annotations },
  ingress: { memo, executeImmediate, status, statusMessage, ingressItems: [...] },
  metrics:  [ { name, value, tags } ],
  messages: [ LogMessage ]
}
```
`TransformEvent`: `{ name, did, content[], metadata, deleteMetadataKeys[], annotations, messages[] }`.
`IngressEventItem`: `{ did, deltaFileName, content[], metadata, annotations }`.

**Serialization rules (wire contract):**
- **camelCase** field names.
- **Null-tolerant.** Omitting null fields is preferred (smaller payloads), but core accepts
  explicit `null`/`{}` — a kit is not required to omit them.
- **Timestamps** are **RFC3339 / ISO-8601 with sub-second precision** (offset or `Z` — core
  accepts both) **or numeric epoch** (seconds or milliseconds). Core tolerates each; the
  reference kits vary (Java/Go emit offset RFC3339, C++ emits UTC `Z`, Python emits numeric
  epoch). Prefer UTC `Z` for canonical, human-readable output. Just be consistent and parseable.
- Tolerate unknown incoming fields (forward-compatible).
- Support large string values (reference allows ~16 MiB strings).

**Heartbeat & long-running tasks** (housekeeping, via Redis hashes):
- Every ~10s, `HSET` the current timestamp for each action name in hash
  `org.deltafi.action-queue.heartbeat`.
- Track in-flight executions and report them (with their `startTime`) into hash
  `org.deltafi.action-queue.long-running-tasks` (key like `clazz:action#thread:did`), removing
  them on completion. **Computing how long is "long-running" (the ~30s threshold) is a core/UI
  concern** — the kit's job is to publish in-flight tasks with start times, not to gate on age.

---

## 9. Lookup Tables

A client-side feature for querying core-hosted lookup tables, plus a supplier mechanism for
plugins that *provide* table data.

### 9.1 Querying (client)

`lookup(tableName, options) → results`. Options (all optional): matching column values
(column → set of allowed values), result columns (projection), sort column + direction (ASC/DESC),
offset, limit. Results: `{ totalCount, created, modified, rows[] }` where each row is a
string→string map (and a caching hint such as `ttlSeconds` where provided).

Transport: a fixed **GraphQL** query to `{CORE_URL}/api/v2/graphql`; uploads via REST
`{CORE_URL}/api/v2/lookup/{table}`. Admin headers: `X-User-Permissions: Admin`,
`X-User-Name: <kit-id>`.

**Caching** (idiomatic, in-process): cache results keyed by a canonicalized query (sort the
columns/values for a stable key). Reference TTLs: success ~30s, failure ~1s. Use a
**single-flight / request-coalescing** mechanism so concurrent misses for the same key issue one
query. Evict periodically (~60s).

### 9.2 Supplying

A plugin component bound to one table definition: `table() → definition`,
`getRows(variables, matchingColumnValues, resultColumns) → rows`. Definition:
`{ name, columns[], keyColumns[], serviceBacked, backingServiceActive, pullThrough,
refreshDuration, variables[] }`.

A supplier runner listens on per-table queues `lookup-table-event-{name}`, invokes the matching
supplier (inside a fault barrier), and publishes a result
`{ lookupTableEventId, lookupTableName, rows }` back to the event's reply id. Initial table data
is uploaded to core once after registration. Provide convenience suppliers backed by packaged
CSV/JSON resources.

---

## 10. Artifacts  *(STREAMING IS MANDATORY)*

A versioned binary-artifact registry (ML models, config blobs, etc.) over core's REST API at
`{CORE_URL}/api/v2/artifacts`, keyed by `owner / name / version` with aliases (default alias
`active`). **Scopes**: plugin-owned (owner = `group.artifact`) and `shared` (readable by all
plugins); a `PLUGIN_FIRST` lookup falls back from plugin to shared.

**Client operations**:
- Publish (plugin or shared): `POST .../{owner}/{name}` as **multipart**, streamed via a pipe
  (do not buffer the whole artifact); returns `{ artifactName, version, isActive, sha256 }`.
- Fetch (buffered): `getArtifact(name[, alias])` → `{ data, manifest }`.
- **Stream (unbuffered, mandatory): `streamArtifact(name[, alias])` → `{ reader/stream, manifest }`**
  — for large artifacts that must not be fully buffered. This streaming fetch is a first-class
  requirement, parallel to content streaming (§5).

A **manifest** is parsed from response headers: `X-Artifact-Version`, `X-Artifact-Sha256`,
`X-Artifact-Description`, `Content-Type`, `X-Artifact-Labels` (JSON map), `X-Artifact-Source-Prefix`.
`isDefault` = label `default=true`.

**Caching & invalidation**: cache with short TTLs (reference: success ~10s, failure ~1s),
single-flight, periodic eviction; **no whole-response timeout** on streaming fetches (use a
response-header timeout instead, to allow large streams). Invalidate via:
1. **Push** — subscribe to the Valkey pub/sub channel `deltafi:artifact:version-changed`; on a
   `VersionChangedEvent { prefix, artifactName, alias, previousVersion, newVersion }`, invalidate
   and fire registered callbacks. Reconnect-with-backoff if the subscription drops.
2. **Poll** — periodically (~60s) compare cached versions against the registry and re-fetch drift.

**Declaration & injection**: let actions declare artifact dependencies idiomatically (field
annotation, constructor default, registered watch). Support `preload` (resolve at startup, fail
fast) vs lazy, and `cacheable` (serve from cache, refreshed by push/poll) vs always-fetch.
Provide `onUpdate` callbacks and a `seedIfMissing` / default-resource mechanism (publish a default
version if none exists; tolerate the 409 race). Fire startup callbacks once after registration.

---

## 11. Logging, Metrics, Errors

### 11.1 Logging — two layers

1. **Operational logs** to stdout (use the language's standard/idiomatic logger; JSON-structured
   is preferred). Include contextual fields like `action` and `did`.
2. **Result-attached log messages** that travel back to core inside the event and are surfaced
   per-DeltaFile in the UI. A `LogMessage` is
   `{ severity (TRACE/INFO/WARNING/ERROR), created, source (action name), message }`. Results
   expose `logInfo` / `logWarning` / `logError`.

### 11.2 Metrics

`Metric { name, value (numeric), tags (string→string map) }`. Actions attach custom metrics to
results; they ride in `ActionEvent.metrics`. (Provide a result-level `addMetric` even though some
reference kits — C++ — define the wire field without an author API yet; a complete kit should
expose it.)

### 11.3 Error model (the canonical mapping)

The worker maps every outcome to an event so an action can never crash the process:
- A returned Error/Filter result → the corresponding ERROR/FILTER event.
- A thrown disposition (error/filter result exception) → the corresponding event, copying
  cause/context/annotations/metrics.
- Domain exceptions from input accessors:
  - "expected content" (no content / index out of range) → ERROR with a clear message.
  - "missing metadata" (`key`) → ERROR "Missing metadata with key {key}".
- A general kit exception → ERROR with its message.
- **Any other uncaught exception/panic** → ERROR with a generic message and the **full stack
  trace in the error context**.
- A `null`/missing result → ERROR.
- A result that fails to convert to an event → ERROR ("invalid action result").

Use the language's natural mechanism (exceptions where idiomatic; returned error state on the
working object in Go-style languages). The *mapping*, not the mechanism, is the contract.

---

## 12. Unit Test Framework  *(SHIPS WITH THE KIT)*

A kit is incomplete without a test framework that lets action authors test their actions fast,
in-process, with **no external Valkey/MinIO/core and no mocks in the data path**. Required pieces:

1. **In-memory content store** implementing the storage interface (§5.3) — real save/load,
   real segment slicing, real range semantics. Plus a fault-injection hook (e.g. "fail after Nth
   save") for exercising error paths. Helpers to create bound content from string/bytes.
2. **A test context builder** producing a populated `ActionContext` wired to the in-memory store,
   with sensible fixed defaults (did, hostname, start time) and helpers to construct typed inputs.
3. **Runners that exercise the real production surface** — the same dispatch path actions hit in
   production (build input → invoke → produce event), parameterized by the action and its typed
   params: `runTransform`, `runEgress`, `runIngress`, `runJoin`.
4. **Result/content assertions** — fluent or helper assertions over the produced event:
   `requireTransform` / single-transform, `requireError(cause)`, `requireFilter(message)`,
   `requireEgress`, `requireIngress`, plus content/metadata/annotation/message/metric checks.
5. **Test doubles for side services** that are *not* the unit under test — in-memory/mock lookup
   and artifact clients with canned results and call recording, swappable safely in parallel
   tests.

Build on the language's standard test framework (JUnit/AssertJ, pytest, `go test`/`-race`,
Catch2, etc.). Run the suite under the race/thread sanitizer where the language supports it (the
Go kit's acceptance target runs `-race`); the worker model is concurrent and must be tested as
such.

**No mocks in the data path**: tests use the real in-memory storage and real serialization. Mocks
are only acceptable for orthogonal side services (lookup/artifact) when they are not what the test
is exercising.

---

## 13. Configuration

All configuration comes from environment variables with sensible defaults. The canonical set:

| Variable | Purpose | Reference default |
|---|---|---|
| `CORE_URL` | Core base URL (registration `/plugins`; GraphQL/lookup/artifacts under `/api/v2`). | varies; e.g. `http://deltafi-core-service` |
| `VALKEY_URL` | Valkey/Redis `host:port`. | e.g. `deltafi-valkey-master:6379` |
| `VALKEY_PASSWORD` | Valkey AUTH. | — |
| `MINIO_URL` | S3/MinIO endpoint. | e.g. `deltafi-minio:9000` |
| `MINIO_ACCESSKEY` / `MINIO_SECRETKEY` | S3 credentials. | `deltafi` / `password` |
| `STORAGE_BUCKET_NAME` | Content bucket. | `storage` |
| `IMAGE` / `IMAGE_PULL_SECRET` | Reported in registration. | — |
| `PROJECT_GROUP` / `PROJECT_NAME` / `PROJECT_VERSION` | Plugin coordinates. | — |
| `APP_NAME` | Pod/container name for task tracking; falls back to hostname. | hostname |
| `ACTION_THREADS` | Default worker count. | `1` |
| `ACTIONS_ACTIONTHREADS_{NAME}` | Per-action worker count. | — |
| `TABLE_LOOKUP_CACHE_TTL` / `TABLE_LOOKUP_FAILURE_CACHE_TTL` | Lookup cache TTLs (s). | `30` / `1` |
| `ARTIFACT_CACHE_TTL` / `ARTIFACT_FAILURE_CACHE_TTL` | Artifact cache TTLs (s). | `10` / `1` |
| TLS cert paths (`CERT_PATH` / `KEY_PATH` / `CA_CHAIN_PATH`) | Mutual TLS to core; plaintext if absent. | `/certs/tls.crt` etc. |

Plus arbitrary secret env vars referenced by `EnvVar` parameters (§6), resolved on the worker.

**Connection pool sizing**: the Valkey pool must accommodate every concurrently-blocking
`BZPOPMIN` (one per action worker/poller) plus housekeeping (heartbeat, lookup supplier runner,
artifact listener). Size it as Σ(per-action worker counts) + overhead.

**Health probe**: create `/tmp/running` when ready; remove on shutdown. **Target design**
replaces this with a distroless-friendly HTTP health endpoint (`httpGet` probe) — see §1.1.

**TLS**: if a cert/key pair is present, use mutual TLS to core; if absent, fall back to plaintext.
Cert and key must both exist or neither.

---

## 14. Security & Interop Notes

- **Secrets stay out of band**: only `EnvVar` *names* travel through config and the queue; values
  are resolved from the environment on the worker. Never serialize secret values.
- **S3 signing**: if not using an SDK, sign requests with AWS Signature V4 (region `us-east-1`,
  service `s3`), path-style addressing. The C++ kit hand-rolls SigV4; the Go kit uses the AWS SDK.
- **Admin headers** on core GraphQL/REST calls: `X-User-Permissions: Admin`,
  `X-User-Name: <kit-identifier>`.
- **Object-key layout and queue semantics are interop contracts** — reproduce them exactly.

---

## 15. Idiomatic Implementation (REQUIRED)

The contracts above are fixed; **their expression must be native to the target language**. Do not
transliterate Java (or any reference) into the new language. Concretely:

- **Action declaration / discovery**: use the idiomatic mechanism — annotations/reflection
  (Java), decorators + package scan (Python), generics + self-registration in init (Go),
  concepts + registration macros (C++), or explicit registration lists. Don't force inheritance
  where composition/interfaces/traits are idiomatic.
- **Result union**: sealed types / tagged unions / `variant` / sum types / marker interfaces —
  whatever the language offers for "one of these types".
- **Concurrency**: goroutines+channels, thread pools, async/await, or threads — match the
  language's concurrency model; just preserve the one-poller/N-workers semantics, fault barriers,
  and graceful 30s drain.
- **Errors**: exceptions where idiomatic; returned error state where idiomatic (Go). Preserve the
  §11 mapping regardless.
- **Streaming**: use the language's native stream/reader/writer abstractions for content and
  artifacts; preserve bounded memory.
- **Serialization**: use the language's standard JSON library, but enforce the wire rules
  (camelCase, omit-null, time format) — via field tags, naming strategies, or custom
  serializers as idiomatic.
- **Config & schema**: typed-config and schema-generation idioms native to the language.
- **Immutability, RAII/ownership, error types, naming** — follow the language's conventions and
  community style guide. Code in the new kit should read as if written by an expert in that
  language, not ported from another.

---

## 16. Conformance Checklist (the minimal contract)

A wire-conformant kit must:

1. **Register** by POSTing the plugin registration (with per-action descriptors carrying
   JSON-schema parameters and optional `ActionOptions`) to `{CORE_URL}/plugins`, with retry and
   fail-fast-on-4xx.
2. **Poll** each action's input ZSET via `BZPOPMIN`, one poller + N workers per action.
3. **Bind** the (possibly all-string) `actionParams` to typed parameters with coercion; project
   each DeltaFile message into typed input; lazily read content from S3/MinIO by the object key
   `did[:3]/did/uuid`, using **server-side Range** reads.
4. **Invoke** the action's single method inside a fault barrier; support join (merge → transform)
   for join-capable transforms.
5. **Save** new content via **streaming, bounded-memory** uploads (5 MiB parts, unknown-size
   supported).
6. **Map** the outcome (or thrown disposition / uncaught error) to an `ActionEvent` with the
   correct `type` and a single populated payload, plus metrics and log messages — following the
   §11 error mapping. An action must never crash the worker.
7. **GC** orphaned content (saved-but-unreferenced segments) after each execution.
8. **Publish** the event via `ZADD` (score = epoch millis) to `dgs` or `dgs-{returnAddress}`,
   using camelCase JSON with RFC3339/`Z`-or-epoch timestamps (null-tolerant).
9. **Heartbeat** and report long-running tasks in the Valkey hashes.
10. **Signal readiness** with `/tmp/running` and **drain gracefully** (30s) on SIGTERM/SIGINT.

Additive but expected for a complete kit: **content & artifact streaming** (mandatory per the
cross-cutting mandate), **the unit-test framework** (§12), **lookup tables** (§9), the **artifact
registry** (§10), the **`EnvVar`/`DataSize`** parameter types (§6), and **idiomatic
implementation** throughout (§15).
```

---

# Appendix A — Complete DeltaFi Action Kit API

This appendix is the full public API surface of a DeltaFi action kit, expressed in
language-neutral signatures. Notation: `Type` for types, `name(args) -> Return` for operations,
`?` for optional/nullable, `[]` for a list/array, `{}` for a map (default `string->string` unless
noted). Render each signature in the target language's idiom (methods vs free functions, builders
vs setters, sum types vs class hierarchies). Field names shown in `JSON:` blocks are the **wire
contract** (camelCase, omit-null).

## A.1 Action Interfaces

What an action author implements. Exactly one method per action type; `Params` may be an empty
type.

```
TransformAction:
    transform(context: ActionContext, params: Params, input: TransformInput)
        -> TransformResult | TransformResults | ErrorResult | FilterResult

EgressAction:
    egress(context: ActionContext, params: Params, input: EgressInput)
        -> EgressResult | ErrorResult | FilterResult

TimedIngressAction:
    ingress(context: ActionContext, params: Params)
        -> IngressResult | ErrorResult | FilterResult

Join (mixin on a TransformAction):
    join(inputs: TransformInput[]) -> TransformInput      // default: concat content, merge metadata
```

Action metadata exposed to the kit (however idiomatic — traits, annotations, overrides):
```
actionType()    -> "TRANSFORM" | "EGRESS" | "TIMED_INGRESS"
name()          -> string        // qualified: "<group-or-domain>.<TypeName>"
description()   -> string
paramType()     -> Type          // empty type if no params
actionOptions() -> ActionOptions?    // optional rich docs (§A.10)
supportsJoin()  -> bool          // true if the action implements Join
```

## A.2 ActionContext

```
ActionContext:
    // identity / runtime fields (read-only to the action)
    did            -> string (UUID)
    deltaFileName  -> string
    dataSource     -> string
    flowName       -> string
    flowId         -> string (UUID)
    actionName     -> string
    actionVersion  -> string
    hostname       -> string
    systemName     -> string
    startTime      -> timestamp
    memo           -> string?            // cross-run state for timed ingress
    join           -> JoinConfiguration?
    joinedDids     -> string[]

    childContext() -> ActionContext      // copy with a freshly generated UUID did

    // Content-save surface (track segments for orphan GC). Placement is idiomatic: a kit MAY put
    // these on the context (Go does) or expose saving on the result/content types (Java, Python,
    // C++) — both are conformant. The streaming variants are mandatory wherever they live.
    save(data: bytes,  name, mediaType)        -> ActionContent
    saveString(data: string, name, mediaType)  -> ActionContent
    saveFromReader(reader, name, mediaType)     -> ActionContent   // streaming, size discovered
    saveFromWriter(writeFn, name, mediaType)    -> ActionContent   // streaming producer callback

JoinConfiguration:
    maxAge       -> number (seconds)
    minNum       -> int
    maxNum       -> int
    metadataKey  -> string
```

## A.3 Input Types

```
TransformInput:
    content                  -> ActionContent[]
    metadata                 -> {}
    hasContent()             -> bool
    firstContent()           -> ActionContent       // throws ExpectedContent if empty
    contentAt(i: int)        -> ActionContent        // throws ExpectedContent if out of range
    contentNamed(name)       -> ActionContent?
    getMetadata(key)         -> string               // throws MissingMetadata if absent
    getMetadataOrDefault(key, default) -> string

EgressInput:
    content       -> ActionContent?     // first content item, or null
    metadata      -> {}
    hasContent()  -> bool
    // metadata accessors as above

IngressInput:                            // empty; timed ingress receives no DeltaFile
```

Incoming per-message wire shape:
```
JSON DeltaFileMessage: { metadata: {}, contentList: [ Content ] }
```

## A.4 Content & Segments

```
ActionContent:
    name        -> string ;  setName(name)
    mediaType   -> string ;  setMediaType(type)
    size()      -> int64
    tags        -> string[] ; addTag(t) ; removeTag(t) ; hasTag(t) -> bool ; clearTags()

    // lazy load (streaming variants mandatory)
    loadBytes()   -> bytes
    loadString()  -> string
    loadReader()  -> stream                         // streaming read

    // zero-copy (no I/O): operate on segment references
    copy()                              -> ActionContent
    subcontent(offset, size [, name, mediaType]) -> ActionContent
    append(other: ActionContent)
    prepend(other: ActionContent)

    objectNames() -> string[]           // S3 keys of referenced segments

Segment:
    uuid    -> string (UUID)
    offset  -> int64
    size    -> int64
    did     -> string (UUID)
    objectName() -> string              // "<did[:3]>/<did>/<uuid>"   (WIRE CONTRACT)

JSON Content: { name, mediaType, segments: [ {uuid,offset,size,did} ], tags?: [] }
```

## A.5 Content Storage Service

```
ContentStorageService:
    save(did, data: bytes)            -> Segment
    saveFromReader(did, reader [, size]) -> Segment    // streaming upload; size may be unknown
    saveMany(did, items: [SaveManyContent]) -> Content[]
    load(segments: Segment[])         -> bytes
    loadReader(segments: Segment[])   -> stream         // streaming read (server-side Range)
    delete(content | segments)
    getTotalSize(segment)             -> int64

ObjectReference: { bucket, objectName, offset, size }   // offset/size express server-side Range
SaveManyContent: { name, mediaType, content: bytes }
```

Streaming requirements (see §5): 5 MiB multipart upload parts; per-segment `Range:
bytes=offset-(offset+size-1)` reads; at most one open object body at a time; counting reader for
unknown-size uploads.

## A.6 Result Types

Common base (all results):
```
Result (base):
    context           -> ActionContext
    addMetric(metric: Metric)            -> self
    logInfo(message)    -> self
    logWarning(message) -> self
    logError(message)   -> self
```

```
TransformResult:
    addContent(ActionContent | [])               -> self
    saveContent(bytes|string, name, mediaType)   -> ActionContent (appended)
    saveContentFromReader(reader, name, mediaType) -> ActionContent   // streaming
    addMetadata(key, value) | addMetadata({})    -> self
    deleteMetadataKey(key)                       -> self
    addAnnotation(key, value) | addAnnotations({}) -> self
    JSON: { did, content[], metadata, deleteMetadataKeys[], annotations }

TransformResults:                                  // 1 -> N split
    addChild(name?) -> ChildTransformResult         // child gets a fresh did
    JSON: transform[] = [ child TransformEvent, ... ]

ChildTransformResult:  (extends TransformResult, adds optional deltaFileName -> JSON "name")

EgressResult:
    // header only, no payload

IngressResult:
    setMemo(string) ; setStatus(HEALTHY|DEGRADED|UNHEALTHY) ; setStatusMessage(string)
    setExecuteImmediate(bool)
    addItem(name?) -> IngressResultItem
    JSON: { memo, executeImmediate, status, statusMessage, ingressItems[] }

IngressResultItem:   (content/metadata/annotation builders like TransformResult; fresh did)
    JSON: { did, deltaFileName, content[], metadata, annotations }

ErrorResult:
    new(context, cause [, context-detail] [, throwable])
    addAnnotation(k,v)
    JSON: { cause, context, annotations }

FilterResult:
    new(context, message [, context-detail])
    addAnnotation(k,v)
    JSON: { message, context, annotations }
```

Thrown disposition (where idiomatic):
```
ErrorResultException:  { errorCause, errorContext, cause, annotations, metrics }  -> ErrorResult
FilterResultException: { filterCause, filterContext, annotations, metrics }       -> FilterResult
```

## A.7 Result Event (wire output)

```
JSON ActionEvent:
{
  did, flowName, flowId, actionName,
  start, stop,                              // RFC3339 (offset or Z, sub-second) OR numeric epoch
  type,                                     // TRANSFORM | EGRESS | INGRESS | ERROR | FILTER
  transform?: [ TransformEvent ],
  error?:   { cause, context, annotations },
  filter?:  { message, context, annotations },
  ingress?: { memo, executeImmediate, status, statusMessage, ingressItems: [ IngressEventItem ] },
  metrics?:  [ { name, value, tags } ],
  messages?: [ LogMessage ]
}

TransformEvent:   { name, did, content[], metadata, deleteMetadataKeys[], annotations, messages[] }
IngressEventItem: { did, deltaFileName, content[], metadata, annotations }
```

## A.8 Parameters & Special Types

```
Parameters:  declared as a typed config object; JSON-schema generated for registration.
             Values may arrive as strings and MUST be coerced to declared types.

EnvVar:                                   // secret indirection
    new(name)                             // name validated ^[A-Z][A-Z0-9_]*$
    resolve()              -> string?     // reads env at runtime on the worker
    resolveOrDefault(d)    -> string
    isSet()                -> bool
    schema: { type: string, x-deltafi-type: "EnvVar" }   // serializes as the NAME only

DataSize:
    new("10MB")            // units B/KB/MB/GB/TB, 1024-based
    bytes()                -> int64
    schema: { type: string, pattern: "^[0-9]+(B|KB|MB|GB|TB)$", x-deltafi-type: "DataSize" }
```

Schema generation: camelCase keys; `additionalProperties:false`; honor `description`, `default`,
`enum`, `required`, `minimum`/`maximum`; inline `$ref`/`$defs`.

## A.9 Logging & Metrics

```
LogMessage:  { severity: TRACE|INFO|WARNING|ERROR, created: timestamp, source: string, message: string }
Metric:      { name: string, value: number, tags: {} }
    // Required surface: the {name, value, tags} wire field + a result-level addMetric (§11.2).
    // The STATSD rendering (metricName() -> "name;k1=v1") and '='/';' tag validation are a
    // Java/core-internal concern, NOT required kit API.
```

## A.10 ActionOptions (registration docs)

```
ActionOptions:  { description, inputSpec, outputSpec, filters[], errors[], notes[], details }
InputSpec:      { contentSummary, contentSpecs: [ContentSpec], metadataSummary, metadataDescriptions: [KeyedDescription] }
OutputSpec:     InputSpec + { passthrough: bool, annotationsSummary, annotationDescriptions: [KeyedDescription] }
ContentSpec:    { name, mediaType, description }
KeyedDescription: { key, description }
DescriptionWithConditions: { description, conditions[] }    // filters/errors entries
```

## A.11 Plugin Registration API

```
PluginRegistration (JSON POST {CORE_URL}/plugins):
{
  image, imagePullSecret,
  pluginCoordinates: { groupId, artifactId, version },
  displayName, description, actionKitVersion,
  dependencies: [ { groupId, artifactId, version } ],
  actions: [ ActionDescriptor ],
  variables: [ Variable ],
  flowPlans: [ <raw JSON> ],
  lookupTables: [ LookupTableDefinition ],
  integrationTests: [ <raw JSON> ]
}

ActionDescriptor: { name, type, supportsJoin, schema: <JSON Schema>, actionOptions?, docsMarkdown?, description?(deprecated) }
Variable:         { name, description, dataType, required, defaultValue, value, masked }

Transport: POST JSON; retry ~4x (500ms/2s/5s), 30s timeout; fail-fast on 4xx; retry 5xx/IO.
```

## A.12 Queue / Transport API (Valkey)

```
ActionEventQueue:
    takeAction(actionName)                 -> ActionInput      // BZPOPMIN on action's ZSET
    putResult(event: ActionEvent, returnAddress?)              // ZADD score=epoch-ms to dgs[-returnAddress]
    setHeartbeat(actionName)                                   // HSET org.deltafi.action-queue.heartbeat
    recordLongRunningTask(execution) / removeLongRunningTask(execution)   // ...long-running-tasks hash
    takeLookupTableEvent(names...) / putLookupTableResult(...)
    subscribe(channel)                     -> stream of messages  // for artifact version-changed

ActionInput (JSON intake):
{
  deltaFileMessages: [ DeltaFileMessage ],
  actionContext: { did, deltaFileName, dataSource, flowName, flowId, actionName,
                   actionVersion, hostname, systemName, memo, join, joinedDids },
  actionParams: { ... },                   // raw; may be all-strings
  queueName, returnAddress
}
```

Queue primitives: ZSET per action, score = epoch millis; `BZPOPMIN` intake (≈5s timeout loop);
`ZADD` publish (`NX` only for input enqueues, not result/reply puts); result queue `dgs` or
`dgs-{returnAddress}`. Hashes:
`org.deltafi.action-queue.heartbeat`, `org.deltafi.action-queue.long-running-tasks`. Pub/sub
channel `deltafi:artifact:version-changed`.

## A.13 Lookup Tables API

```
LookupClient:
    lookup(tableName, options: LookupOptions) -> LookupResults
    getLookupTables()                         -> LookupTable[]
    createLookupTable(table)                  -> Result
    upsertRows(tableName, rows: [{}])         -> Result      // batched (~10k)
    removeRows(tableName, rows)               -> Result
    uploadTable(tableName, fileType: JSON|CSV, contents)     // replaces all rows
    setBackingServiceActive(tableName, bool)

LookupOptions:  { matchingColumnValues: {col -> [values]}, resultColumns: [], sortColumn,
                  sortDirection: ASC|DESC, offset, limit }     // all optional
LookupResults:  { totalCount, created, modified, ttlSeconds?, rows: [ {} ] }

LookupTableSupplier (plugin provides data):
    table()  -> LookupTableDefinition
    getRows(variables, matchingColumnValues, resultColumns) -> Row[]

LookupTableDefinition: { name, columns[], keyColumns[], serviceBacked, backingServiceActive,
                         pullThrough, refreshDuration, variables[] }
Row: immutable view over {} ;  get(key) ; has(key) ; len() ; all()
```

Transport: GraphQL `{CORE_URL}/api/v2/graphql`; REST upload `{CORE_URL}/api/v2/lookup/{table}`;
headers `X-User-Permissions: Admin`, `X-User-Name: <kit-id>`. In-process cache: canonicalized
key, success TTL ~30s / failure ~1s, single-flight, ~60s eviction. Supplier runner listens on
`lookup-table-event-{name}`, replies to the event reply id.

## A.14 Artifacts API

```
ArtifactClient:
    publishArtifact(name, manifest, reader [, makeActive])        -> PublishResponse   // streamed multipart
    publishSharedArtifact(name, manifest, reader [, makeActive])  -> PublishResponse
    getArtifact(name [, alias])         -> FetchedArtifact         // buffered
    getSharedArtifact(name [, alias])   -> FetchedArtifact
    streamArtifact(name [, alias])      -> ArtifactStream          // STREAMING (mandatory)
    streamSharedArtifact(name [, alias]) -> ArtifactStream
    onUpdate(name, callback) / onSharedUpdate(...)               // version-change callbacks
    onUpdateFetch(...) / onUpdateFetchWithDefault(default, ...)  // auto-fetch variants
    seedIfMissing(name, defaultResource)
    fireStartupCallbacks()

FetchedArtifact:  { data: bytes, manifest: ArtifactManifest }
ArtifactStream:   { reader: stream, manifest: ArtifactManifest }     // closeable
ArtifactManifest: { version, description, contentType, sha256, labels: {} }  ; isDefault()
PublishOptions:   { version, description, makeActive?, alias, labels: {} }
PublishResponse:  { artifactName, version, isActive, sha256 }
ArtifactScope:    PLUGIN_ONLY | SHARED_ONLY | PLUGIN_FIRST    (default alias "active")
VersionChangedEvent: { prefix, artifactName, alias, previousVersion, newVersion }
```

Transport: REST `{CORE_URL}/api/v2/artifacts/{owner}/{name}[/versions/{alias}]`; owner =
`group.artifact` or `shared`; admin headers. Manifest from `X-Artifact-*` response headers. Cache
success ~10s / failure ~1s, single-flight; no whole-response timeout on streams (use header
timeout). Invalidate via pub/sub `deltafi:artifact:version-changed` + ~60s poll.

## A.15 Errors

```
MissingMetadataError:  { key }                  // -> ERROR "Missing metadata with key {key}"
ExpectedContentError:  { index, count }         // -> ERROR "Action received no content" / OOB
ActionKitException:    general kit error        // -> ERROR with message
// any uncaught exception/panic               -> ERROR with full stack trace in context
```

## A.16 Unit Test API

```
InMemoryContentStore : ContentStorageService     // real save/load/range, fault injection (failAfterNth)
    createContent(name, mediaType, data) -> ActionContent
    createStringContent(name, mediaType, str) -> ActionContent

TestContext:                                      // ActionContext wired to in-memory store
    new(did)
    content(name, mediaType, data) -> ActionContent
    newDeltafile(...) / newTimedIngressDeltafile()
    setLookupClient(mock) / setArtifactClient(mock)    // parallel-safe swap + restore

// runners exercise the REAL production dispatch surface
runTransform(action, params, input)  -> ActionEvent
runEgress(action, params, input)     -> ActionEvent
runIngress(action, params)           -> ActionEvent
runJoin(action, params, inputs)      -> ActionEvent

// assertions over the produced event
requireTransform(event) / requireSingleTransform(event)
requireError(event [, expectedCause])
requireFilter(event [, expectedMessage])
requireEgress(event)
requireIngress(event)
// plus content / metadata / annotation / message / metric checks

MockLookupClient:   setResults(table, results) ; setError(table, msg) ; callCount()
MockArtifactClient: setArtifact(name, alias, fetched) ; publishCount()
```

No mocks in the data path: storage and serialization are real; mocks only for orthogonal side
services not under test. Run under the language's race/thread sanitizer where available.

---

# Appendix B — The Hello-World Reference Plugin

Every action-kit implementation ships with a **hello-world plugin**: a reference plugin that
exercises every action type and kit feature end-to-end, serves as the worked example for plugin
authors, and is the live conformance test (its integration tests run in CI against a real
DeltaFi deployment). A language port is not complete until its hello-world plugin exists, builds,
deploys, and passes the shared integration tests.

> **Source of truth: the Java (`deltafi-java-hello-world`) and Go (`deltafi-go-hello-world`)
> plugins.** They are the canonical, most complete references — both implement the full action
> set including the streaming demos, both are standalone repos with their own CI and integration
> jobs. The Python and C++ hello-world plugins are parity ports and lag in places (Python omits
> the streaming actions and adds a language-specific `exec` action; C++ omits streaming, has no
> own CI, and bakes coordinates into a macro). **When the references disagree, follow Java and
> Go.** A new port should mirror the Java/Go plugin feature-for-feature, including the streaming
> actions.

## B.1 What the plugin must contain

A conformant hello-world plugin has all of the following. Behaviors, names, metadata keys, and
integration-test expectations are **identical across languages** — only namespacing (`hello-world-<lang>-*`
topics, `org.deltafi.<lang>-hello-world.<Action>` action types) and language-idiomatic
mechanics differ.

1. **Entry point / bootstrap** that resolves plugin coordinates, discovers/registers all actions
   and lookup suppliers, registers an artifact watch (`config.yaml`), and runs the plugin.
2. **One action of every type** (§B.2).
3. **Four lookup-table suppliers** (§B.3).
4. **A `flows/` directory** of flow plans plus a variables file (§B.4).
5. **An `integration/` directory** with three integration tests, two of which run in CI (§B.5).
6. **Unit tests** for every action using the kit's test framework (§B.6).
7. **Container packaging + CI** that builds, tests, publishes, dev-installs, and runs the
   integration tests (§B.7).

## B.2 Actions (the canonical set)

The plugin defines one action of each type. The behaviors below are the contract (drawn from the
Java/Go references); replicate them exactly so the shared integration tests pass.

| Action (role) | Type | Canonical behavior |
|---|---|---|
| **Transform** | `TRANSFORM` | Appends `"\n<ActionName> did a great job"` to content; optionally uppercases (`convertToUpper`). **Filters** when the DID starts with `didFilterPrefix` (default `2`); **errors** (with error context) when the DID starts with `didErrorPrefix` (default `44`); `always*OnPrefixHit` flags force the disposition. Saves new content tagged `hello-file`; adds metadata; adds annotations. Performs **3 lookups** (RGB row, purple count, McLaren drivers) and attaches results as metadata. Reads a versioned **artifact** (`config.yaml`) via a watch/`onUpdate` callback. Honors an **env-var** toggle (`SKIP_TABLE_LOOKUP`). Declares **nested/list parameter structures** (`structure1/2/3List`) to demonstrate schema generation. Returns Transform / Filter / Error. |
| **Transform-many (split)** | `TRANSFORM` | Splits one input into **N children** (`numberOfChildren`, default 2). Each child gets the saved content, `transformManyKey` metadata, and a `childIndex` annotation; odd-indexed children also get a `did` metadata entry. Manually **publishes an artifact** (`hello.txt`) if absent, guarded to run once. Returns Transforms (children). |
| **Egress** | `EGRESS` | Pretends to egress. **Streams** the content scanning for the byte marker `"error"` (handling read-boundary straddle); **errors** if found, otherwise logs the byte count and metadata. Returns Egress / Error. |
| **Join + Transform** | `TRANSFORM` (+Join) | `join` reverse-merges the inputs and sums each input's `index` metadata into `indexSum`. `transform` concatenates the joined content into `single-<indexSum>`, tags it `joined`, and annotates `indexThresholdMet=true` when `indexSum >= indexThreshold` (default 50). Errors if `indexSum` is missing. |
| **Timed Ingress (source)** | `TIMED_INGRESS` | Emits one new DeltaFile per tick, filename `<flow>-<index>`, content `"Some content, part N"`. Uses `memo` to **increment the index across runs**. Routes downstream by setting a `hello` metadata value (transform vs join) and a `join-group` even/odd value; varies metadata/annotations by `index % 3`. Sets `executeImmediate=false` and a status message. |
| **Streaming pair** *(Java/Go — required for a full port)* | `TRANSFORM` | A **Lorem Ipsum** action that passes input through by reference and **streams** `byteCount` generated bytes via a writer-callback save; and a **Capitalize** action that **streams** each text content through a charset-preserving uppercaser (load-reader → save-reader), passing non-text content through. Together they prove bounded-memory streaming (§5) and back the `streaming` flow. |

These collectively demonstrate: content save/load (buffered **and streaming**), metadata add/delete,
annotations, content tags, child/split fan-out, join/aggregate, filter vs error vs success
dispositions, lookup tables, artifacts (watch + manual publish), env-var secrets, nested
parameter schemas, and memo-based source state.

## B.3 Lookup-table suppliers

Four suppliers, backed by two shipped data files (`lookup-tables/cities.json`,
`lookup-tables/f1-drivers.csv` — byte-identical across languages):

- **Scheduled** — columns `column_a/b/color`, `refreshDuration = PT1M`, service-backed,
  dynamically returns a `red` row (the integration test asserts `A3`-matching content).
- **CSV** — `<lang>_csv_lookup_table` served from the F1-drivers CSV resource, not service-backed.
- **JSON** — `<lang>_json_lookup_table` served from the cities JSON resource.
- **Pull-through** — `pullThrough = true`, consumes action **variables** (`didErrorPrefix`,
  `convertToUpper`).

## B.4 Flows and variables

`flows/` contains the topology (topics namespaced `hello-world-<lang>-*`):

- **Data sources**: one **timed** source (cron `*/5 * * * * *`, with `metadata` and an
  `annotationConfig` carrying `metadataPatterns` / `discardPrefix` / `annotations`), plus three
  **REST** sources (one each for the transform, join, and transform-many topics).
- **Transforms**: a **route-or-filter** transform that publishes by `metadata['hello']` to the
  transform/join topics and **filters by default**; the **transform**, **transform-many**, and
  **join** flows (the join flow carries a `join` block: `maxAge PT35S, minNum 2, maxNum 3,
  metadataKey join-group`); and a **streaming** flow that chains core `DeleteContent` →
  LoremIpsum → Capitalize (Java/Go).
- **Data sink**: one sink wiring the egress action.

Flow parameters reference plugin **variables** via `${...}` substitution. The **variables file**
defines (at minimum): `convertToUpper` (bool), `numChildren` (number, required, default 2),
`didErrorPrefix` / `didFilterPrefix` (string), `alwaysErrorOnPrefixHit` /
`alwaysFilterOnPrefixHit` (bool), and `loremIpsumSize` (number, for the streaming flow). Each
variable has `name / description / dataType / required / defaultValue`. The file may be JSON or
YAML (Java/C++ use JSON; Go/Python use YAML) — use whatever the kit's flow loader accepts.

## B.5 Integration tests

`integration/` ships three tests with **identical expected results across all languages**:

- **`join-group-test`** — three inputs with `join-group` metadata; expects one COMPLETE joined
  child (`indexSum=102`, `indexThresholdMet=true`, content `single-102`), one ERROR (unjoined),
  one COMPLETE.
- **`transform-lookup-test`** — input "My favorite color is RED"; validates lookup metadata
  (`McLaren Drivers`, `purpleCount=0`, `RGB Rows ~ .*A3.*`, table timestamps).
- **`transform-many-test`** — expects two children carrying `childIndex` annotations and the
  transformed content.

Each test declares `plugins`, `dataSources`, `transformationFlows`, `dataSinks`, `inputs` (with
`ingressFileName` / `data` / `metadata`), a `timeout`, and `expectedDeltaFiles` (stage,
`childCount`, `expectedFlows`, `expectedContent` with metadata/annotation matchers; base64 and
macro substitutions like `{{PARENT_DID}}` are supported). **`join-group-test` and
`transform-many-test` run in CI** against a live dev deployment via the
`startIntegrationTest` GraphQL mutation; `transform-lookup-test` ships but is not wired into CI.

## B.6 Unit tests

Every action has unit tests built on the kit's test framework (§12): in-memory content store,
real dispatch runners, and result assertions — covering the success path, the filter path, the
error path, lookup-metadata attachment, child counts/annotations, memo increment, and (for the
streaming actions) proof that content is **not** fully buffered. Side services (lookup/artifact)
are mocked; the data path is real. Run under the language's race detector where available (the Go
plugin has a dedicated race-free-singleton concurrency test and a streaming-no-buffer test).

## B.7 Packaging, build, and CI

- **Containerization**: a multi-stage Dockerfile that builds the plugin and produces a runtime
  image. The image copies the built artifact (named `deltafi-plugin`) plus the `flows/` and
  `integration/` directories, and sets the runtime env (`ACTION_THREADS=1`, a per-action thread
  override to demonstrate concurrency, and artifact/lookup cache-TTL vars).
- **Local-vs-published kit toggle**: the build supports building against a **local checkout** of
  the action kit (for kit development) as well as the **published** kit — via a build arg / build
  context (Go: `KIT_SOURCE` + `go.work`; Java: convention plugin + dependency version; C++:
  `DELTAFI_ACTION_KIT_PATH` / `FetchContent`; the kit version is pinned in the build file).
- **Build tooling**: a `Makefile` (or equivalent) exposing `build`, `test`, `docker`, `publish`
  (multi-arch), and `install` (`deltafi plugin install --wait`), plus a `coordinates` / `manifest`
  query path. The plugin **manifest/coordinates are generated**, not hand-written — coordinates
  are `groupId = org.deltafi.<lang>-hello-world`, `artifactId = <repo name>`, version from the
  build file. (Go carries coordinates in a small `plugin.yaml`; Java/Python derive them from the
  build descriptor; C++ bakes them into the plugin macro — use whatever is idiomatic.)
- **CI**: build + unit test, publish a multi-arch container, dev-install, then run the two
  integration tests. The Apache license `HEADER` is applied to all source files.

## B.8 Porting checklist

To stand up the hello-world plugin for a new language, mirror **Java and Go**:

1. Bootstrap entry point with coordinates, action/supplier discovery, artifact watch, run loop.
2. Implement the six action behaviors in §B.2 exactly (including the streaming pair).
3. Implement the four lookup suppliers (§B.3) over the shared `cities.json` / `f1-drivers.csv`.
4. Copy the `flows/` topology and the variables file (§B.4), renamespaced for the language.
5. Copy the three `integration/` tests (§B.5) verbatim except for namespacing and the base64
   content blobs; wire two into CI.
6. Write unit tests for every action on the kit's test framework (§B.6).
7. Provide the Dockerfile + build tooling + CI with local/published kit modes (§B.7).
8. Deploy and confirm the two CI integration tests pass against a real DeltaFi — that is the
   definition of done for the port.
