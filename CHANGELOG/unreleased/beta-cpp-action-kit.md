# Changes on branch `beta-cpp-action-kit`
Document any changes on this branch here.
### Added
- clang-format and clang-tidy configuration for the **C++ Action Kit**
- CMake sanitizer presets (asan/ubsan/tsan), a `DELTAFI_SANITIZE` option, and a `DELTAFI_WERROR` warnings-as-errors option for the **C++ Action Kit**
- CI pipeline for the **C++ Action Kit** (build, test, ASan/TSan sanitizers, clang-format, clang-tidy)
- `TRACE` log severity and `log_trace` helpers on C++ action results
- Result-level `add_metric` on all C++ action result types (metrics were previously unreachable by action authors)
- C++ egress and timed-ingress actions can now return (and throw) a `FilterResult`
- Thrown `ErrorResultException`/`FilterResultException` dispositions carrying cause/context/annotations/metrics, converted to the matching result by the worker
- `context` field on C++ filter results (`FilterResult`/`FilterEvent`)
- Mutual TLS for the **C++ Action Kit**'s HTTP clients (core registration, artifact registry, lookup queries), auto-detected from cert files mounted at `/certs` (`CERT_PATH`/`KEY_PATH`/`CA_CHAIN_PATH`, enforcing both-cert-and-key-or-neither), matching the Go and Java action kits
- Programmatic plugin dependency declaration via `Plugin::add_dependency()`, serialized into the registration payload
- RAII readiness-probe guard that removes the `/tmp/running` marker file on every shutdown path, including exceptions
- Streaming content storage in the **C++ Action Kit**: bounded-memory S3 multipart upload (single-PUT fast path, abort-on-failure, unknown-size counting), a content-receiver-backed streaming `load_reader`, and a `save_content_from_writer` writer-callback save
- Streaming artifact operations in the **C++ Action Kit**: `stream_artifact`/`stream_shared_artifact` (incremental fetch with the manifest available as soon as the response headers arrive) and `publish_from_reader`/`publish_shared_from_reader` (streamed multipart publish)
- Lookup-table admin operations in the **C++ Action Kit**: `get_lookup_tables`, `create_lookup_table`, `upsert_rows`, `remove_rows`, and `set_backing_service_active` (GraphQL, matching the Java kit); `LookupResults` now carries `ttl_seconds`
- C++ artifact scope resolution: `ArtifactScope` (`PLUGIN_FIRST`/`PLUGIN_ONLY`/`SHARED_ONLY`) on `get_artifact`/`stream_artifact`, so a plugin artifact can fall back to the shared namespace; plus `ArtifactManifest::is_default()`
- C++ test framework: `run_join` runner, `InMemoryContentStore::fail_after_nth`/`size`/`save_count` fault-injection and introspection, `new_deltafile`/`new_timed_ingress_deltafile` input builders, `TestContext::use_lookup`/`use_artifact` mock wiring, and richer assertions (`require_single_transform`, `require_metadata`/`require_annotation`/`require_content`/`require_metric`/`require_message`)
- `deltafi::ActionOptions::from_yaml` in the **C++ Action Kit**, backed by a small header-only block-YAML-subset parser (`internal/simpleyaml.hpp`) and `from_json` on the option types, so actions can declare their documentation from a readable YAML literal instead of a nested struct blob (the hello-world transform now uses it)

### Changed
- **C++ Action Kit** registration now fails fast on 4xx responses, retrying only 5xx and connection errors
- **C++ Action Kit** publishes action results over per-worker Valkey connections instead of one shared connection, removing the process-wide publish lock
- **C++ Action Kit** shutdown enforces a hard 30-second drain deadline, abandoning in-flight work that overruns it
- **C++ Action Kit** long-running-task reporting is gated by the 30s threshold and keyed as `clazz:action#thread:did` to match core
- **C++ Action Kit** poll loops (the action intake loop and the lookup-supplier intake loop) now apply jittered, capped exponential backoff (100ms → 5s, reset after a healthy read, shutdown-cancellable) when the event queue (Valkey) is unreachable, instead of retrying with a fixed 1s sleep. This keeps a Valkey outage from becoming a CPU-pegging retry storm and spreads reconnect attempts across action threads. Matches the shared backoff contract in the Java/Go/Python kits.
- **C++ Action Kit** content and artifact save/load now stream to and from storage in bounded-memory parts instead of buffering whole objects; public `content.hpp` signatures are unchanged, so existing plugins recompile without source changes
- C++ lookup client coalesces concurrent misses for the same key into a single query (single-flight) and evicts expired cache entries on a background timer instead of only lazily on access
- C++ artifact client gained a background poll-drift safety net that periodically re-lists watched artifacts and re-fetches any whose version changed but whose pub/sub notification was missed; its version-change listener now reconnects with jittered exponential backoff instead of a fixed 5s sleep, and default-artifact seeding tolerates an HTTP 409 (concurrent seed) by using the existing artifact

### Fixed
- **C++ Action Kit** reconnect test hung in CI and raced under ThreadSanitizer because the fake RESP test server closed sockets across threads; the harness now uses non-blocking, single-thread-owned sockets
- Removed unused variables in the **C++ Action Kit** that broke `-Werror` builds
- **C++ Action Kit** merged metadata/annotation maps first-wins instead of later-wins: `default_join` and the bulk `add_metadata`/`add_annotations` overloads now let later values overwrite earlier keys
- **C++ Action Kit** input-accessor errors now use the canonical messages ("Missing metadata with key {key}" / "Action received no content") to match the other kits
- **C++ Action Kit** orphan-content GC now covers egress, timed-ingress, and thrown/error paths (previously transform-only, leaking saved content on those paths); an RAII guard ensures a saving action that throws still cleans up
- **C++ Action Kit** uncaught action exceptions now record the exception type alongside the message in the error context

### Removed
- 

### Deprecated
- 

### Security
- C++ plugins now present a client certificate and verify the server against the mounted CA chain when connecting to core over HTTPS (mutual TLS), when certs are present at `/certs`
- C++ S3 content client now supports full mutual TLS (client certificate + CA-chain verification, auto-detected from `/certs`) and honors `MINIO_USE_SSL`, so it can connect to an external S3 fronted by a private CA

### Tech-Debt/Refactor
- C++ test runners (`run_transform`/`run_egress`/`run_ingress`) now dispatch through the same executor as the worker — input building, param deserialization, orphan-content GC, and the fault barrier — instead of calling the action method directly, so unit tests exercise the real dispatch path. The executor construction and fault barrier were extracted into reusable helpers shared by the worker and the test runners.
- Enabled `-Wall -Wextra -Wpedantic` for the **C++ Action Kit**
- Refreshed vendored **C++ Action Kit** dependencies: nlohmann/json 3.12.0, spdlog 1.17.0 (built with `SPDLOG_USE_STD_FORMAT`), Catch2 3.15.2, BS_thread_pool 5.1.0; recorded versions in `vendor/VERSIONS.md`
- Upgraded vendored cpp-httplib 0.16.3 → 0.50.1 and standalone asio 1.30.2 → 1.38.1 for their current OpenSSL/asio-ssl APIs; the kit now links OpenSSL (`CPPHTTPLIB_OPENSSL_SUPPORT`)
- Expanded the C++ `clang-tidy` check set to the full bugprone/performance/modernize/readability groups plus curated cppcoreguidelines (member-initializer, uninitialized member/variable, forwarding, vararg, rule-of-five), trimming the disable list to a small set of opinionated style checks and fixing the surfaced findings across the kit and hello-world; `make lint` reproduces CI (containerized clang-tidy fallback, macOS SDK sysroot)
- `make lint` and the C++ CI jobs now install OpenSSL dev headers for the containerized/clang toolchains

### Upgrade and Migration
- 
