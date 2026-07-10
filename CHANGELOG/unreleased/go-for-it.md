# Changes on branch `go-for-it`
Document any changes on this branch here.
### Highlights
- **Go action kit** API changes are complete.  In an upcoming release, the action kit will soon be declared stable for production.  There may be API breaking changes if issues are found, but there are no known quality issues at this point.

### Added
- Documented result precedence, artifact watch-and-cache, streaming, and action concurrency contract in the Go action kit DESIGN
- Added acceptance-test make target (go test -race) to the Go action kit and plugins, plus a concurrency contract test; race runs need build-base on alpine CI
- Go action kit: Added streaming artifact upload support to the Go action kit so large artifacts no longer need to be fully buffered in memory before publishing.
- Go action kit: Added SaveWriterContent / SaveFromWriter for bounded-memory writer-callback content saves
- Go action kit: Added Fail/Failf convenience methods for converting errors to action error results
- Go action kit: Added SaveContentBatch and SaveWriterContentBatch for multi-item content saves with per-item error aggregation
- Go action kit: Added NewEmbeddedCSVSupplier/NewEmbeddedJSONSupplier for embedded lookup-table resources
- Go action kit: Added worker tests for publish failure, queue backoff/recovery, and shutdown cancellation
- Go action kit: Added custom metric emission support for transform, egress, and timed ingress actions.
- Go action kit: Added Filterf and SetFilterContext to timed ingress actions so a timed ingress can return a FILTER result.
- Go action kit: Added SetFilterContext to transform and egress actions and a context field to filter results.
- Go action kit: Added IngressStatus constants (HEALTHY, DEGRADED, UNHEALTHY) for timed ingress status reporting.

### Changed
- Changed Go action kit content reads to open content segments lazily while streaming, reducing open storage connections for multi-segment content.
- Reordered the Go action kit content-save API to a consistent name/mediaType-first, payload-last signature
- Lookup table rows are now a compile-time read-only Row type (zero-copy, breaking API change)
- Changed Go action kit application logs to structured JSON output.
- Changed Go action kit plugin registration to retry transient failures (network errors and 5xx) with capped exponential backoff up to a configurable budget, while failing fast on 4xx client errors, so a slow-starting Core no longer crash-loops the plugin.

### Fixed
- Fixed Go action kit action dispatch to honor returned DeltaFiles and convert nil action returns into error results.
- Fixed Go action kit registration and dispatch for actions that use pointer parameter types or nil pointer defaults.
- Fixed Go action kit public test runners to mirror production action return semantics.
- Fixed Go action kit lookup table supplier panics so they are logged and surfaced as errors instead of crashing the runtime loop.
- Fixed Go action kit content range validation for invalid subcontent and in-memory test content store reads.
- Fixed Go action kit plugin registration so each HTTP attempt has a timeout and cannot hang startup indefinitely on a stalled Core connection.
- Improved Go action kit artifact version listener logging when the subscription channel closes and reconnects.
- Fixed Go action kit default artifact seeding, child/ingress orphan cleanup, and string-map parameter parsing edge cases.
- Fixed Go action kit long-running task reporting so Core can identify in-flight Go actions and avoid duplicate requeue.
- Fixed Go action kit graceful shutdown so in-flight actions can publish results during the worker drain window.
- Fixed Go action kit platform HTTP clients to preserve default transport timeouts and proxy behavior.
- Fixed Go action kit transform dispatch so error and filter results take precedence over split children.
- Fixed Go action kit parameter coercion for embedded structs and unknown-typed string values.
- Fixed Go action kit Fail(nil) on transform, egress, and timed ingress so a nil error no longer panics the worker.
- Fixed Go action kit DataSize parsing to reject values that overflow int64 instead of silently wrapping to a negative size.
- Fixed Go action kit schema generation to log when a JSON round-trip fails instead of silently registering an empty parameter schema.
- Fixed Go action kit lookup table supplier result publishing to retry transient Valkey failures instead of dropping the reply to Core.
- Fixed Go action kit join-then-transform actions to carry annotations, log messages, and metrics produced during the join phase into the result.

### Removed
- Removed the unused multiCloser helper from the Go action kit.

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Go action kit: Switched from minio client to AWS client for S3 access
- Go action kit: Handled previously-unchecked Close and Remove error returns (errcheck cleanup).

### Upgrade and Migration
- 
