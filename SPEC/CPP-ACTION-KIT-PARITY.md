# C++ Action Kit — Parity Analysis & Plan

Goal: bring **`deltafi-cpp-action-kit`** and **`deltafi-cpp-hello-world`** to full action-kit
parity per [`ACTION_KIT_DESIGN.md`](ACTION_KIT_DESIGN.md), using
[`ACTION_KIT_CONFORMANCE_MATRIX.md`](ACTION_KIT_CONFORMANCE_MATRIX.md) as the starting point and
the Go/Java kits as reference implementations.

This document is a **code-verified** audit (every item below was checked against the current
source, not just copied from the matrix) plus a phased plan. Severity: **MAJOR** = missing
required feature/mandate; **MINOR** = cosmetic/idiomatic/text. No BLOCKERs — the C++ kit is
wire-conformant and interoperates with core today.

## Status (updated 2026-07-10)

Items marked **✅** in the tables below are complete. Bite-sized plans:
`docs/superpowers/plans/2026-07-09-cpp-action-kit-phase0-foundations.md`,
`.../2026-07-01-cpp-action-kit-phase1-api-correctness.md`.

- **Phase 0 (Foundations & tooling) — ✅ DONE.** clang-format/clang-tidy, `-Wall -Wextra -Wpedantic`
  + `DELTAFI_WERROR`, sanitizer presets (asan/ubsan/tsan), git-tag-derived kit version, kit CI, and
  Go-parity Makefile. (Also reworked the `test_queue_reconnect` harness to fix a CI hang + TSan race.)
- **Phase 1 (API correctness) — ✅ DONE.** K-6, K-7, K-8, K-9, K-10, K-17, K-21, K-22, K-23. Gated
  green under format / `-Werror` / asan / tsan; docs + hello-world updated.
- **Phase 2 (Lifecycle & transport hardening) — ✅ DONE.** K-11, K-12, K-13, K-15, K-16, K-25
  complete. K-14 landed as **mutual TLS on the HTTP clients only** (core registration, artifact,
  lookup) via shared `/certs` auto-detect — the true Go/Java parity, since both reference kits leave
  Valkey plaintext. Valkey asio-ssl was dropped (beyond parity); **K-14b (MinIO/S3 TLS) is deferred
  to Phase 3**, folded into the `s3_client.hpp` streaming rewrite to avoid editing that file twice.
  Vendored cpp-httplib 0.16.3→0.50.1 and asio 1.30.2→1.38.1 bumped as T7 step 0. Gated green under
  format / `-Werror` / asan / tsan / `make lint` (0 findings); hello-world `make test` + `make docker`
  green. Bite-sized plan: `.../2026-07-10-cpp-action-kit-phase2-lifecycle.md`.
- **Phase 3 (Content & artifact streaming) — ✅ DONE.** K-1, K-2, K-3, K-4, K-5, and K-14b complete.
  The hand-rolled S3 client now does bounded-memory multipart writes (single-PUT fast path, abort on
  failure), a content-receiver-backed streaming `load_reader`, a writer-callback `save_content_from_writer`,
  and streaming artifact fetch (`stream_artifact`) + publish (`publish_from_reader`). K-14b landed as full
  mTLS on the S3 client via the shared `/certs` helper plus `MINIO_USE_SSL`, so an external S3 behind a
  private CA works. In-process fake-S3 and fake-artifact servers + a SigV4 canonical-query unit vector
  make the flow deterministically testable. Gated green under format / `-Werror` / asan / tsan /
  `make lint` (0 findings); hello-world `make test` + `make docker` green. Plan:
  `.../snazzy-crafting-octopus.md`; design of record: `CPP-LARGE-FILE-STREAMING.md`.
- **Phase 4 (Lookup & artifact completeness) — ✅ DONE.** K-18, K-19, K-20 complete (full Java
  parity, as chosen). Lookup: single-flight coalescing + 60s background evictor, the full admin
  suite (`get_lookup_tables`/`create_lookup_table`/`upsert_rows`/`remove_rows`/`set_backing_service_active`),
  and `ttl_seconds`. Artifact: `ArtifactScope` (PLUGIN_FIRST/PLUGIN_ONLY/SHARED_ONLY) on get + stream,
  `is_default()`, 409-tolerant default seed, a 300s poll-drift safety net (new list-artifacts client
  method), and jittered-backoff listener reconnect (shared `internal/backoff.hpp`). Gated green under
  format / `-Werror` / asan / tsan / `make lint` (0); hello-world `make test` + `make docker` green.
  Deferred (noted): per-callback preload/cacheable toggles (Java `@PluginArtifact`-annotation-specific)
  and scope on the `on_update_fetch` watch (the explicit plugin/shared watch variants remain). The
  three Java-parity divergences this work surfaced are filed as GitLab issues #594/#595/#596 for the
  Go kit.
- **Phase 5 (Test framework) — ✅ DONE.** K-26, K-27, K-28, K-29, K-30 complete. The test runners
  now drive the **real production dispatch** (extracted `make_*_executor` factories + a
  `dispatch_with_barrier` fault-barrier helper shared by the worker and runners), so unit tests
  exercise input building, param deserialization, orphan GC, and the fault barrier. Added `run_join`,
  `InMemoryContentStore::fail_after_nth`/`size`/`save_count`, `new_deltafile`/`new_timed_ingress_deltafile`
  builders, `TestContext::use_lookup`/`use_artifact` wiring, and richer assertions
  (`require_single_transform`/`require_metadata`/`require_annotation`/`require_content`/`require_metric`/`require_message`).
  Gated green under format / `-Werror` / asan / tsan / `make lint` (0); hello-world `make test` +
  `make docker` green — its existing tests run unchanged through the new dispatch path.
- **Phase 6 (Hello-world plugin parity) — ✅ DONE.** H-1..H-8 complete. Added the streaming pair
  (`LoremIpsumAction` writer-callback save + `CapitalizeAction` load-reader→save-writer), the
  `streaming` flow (`DeleteContent → LoremIpsum → Capitalize`), and `loremIpsumSize` (H-1); streaming
  egress with read-boundary straddle handling (H-2); shared `lookup-tables/cities.json` +
  `f1-drivers.csv` served by the JSON/CSV suppliers (H-3); a `.gitlab-ci.yml` extending the repo's
  plugin templates (H-4); the `SKIP_TABLE_LOOKUP` toggle (H-5); the transform's `config.yaml` watch
  (H-6); Apache headers on every source (H-7); and `groupId = org.deltafi.cpp-hello-world` with the
  integration-test refs updated (H-8). hello-world `make test` (52 unit tests) + `make docker` green.
  **Not verifiable here:** H-4's live-deployment integration-test stage — the CI YAML follows the
  established plugin pattern but must be validated on a real pipeline run.

## Corrections to the matrix (verified against current code)

- **Zero-copy composition is present** (matrix ✅ stands): `subcontent`/`subcontent_named`/
  `append`/`prepend`/`copy` live in `types.hpp:101-160`, not `content.hpp`. Not a deficiency.
- **Heartbeat / long-running tasks is worse than the matrix implies**: the kit reports *every*
  in-flight task as long-running on the *first* heartbeat (the 30s threshold constant is defined
  but unused), so this is a live functional divergence from core's expectation, not just a
  cosmetic key-form nit. See K-11.
- **Valkey has no worker-sized pool**: all result publishes serialize through a single shared
  connection under one mutex — a correctness-adjacent throughput bottleneck the matrix only
  flagged as "default pool." See K-12.

---

## Part 1 — Kit deficiencies (`deltafi-cpp-action-kit`)

### Streaming (the headline mandate — §5, §10)

| ID | Sev | Deficiency | Evidence | Reference |
|----|-----|-----------|----------|-----------|
| **K-1** ✅ | MAJOR | **Content upload fully buffers.** `S3Client::save` builds one in-memory body; `save_from_reader` slurps the whole stream first ("cpp-httplib requires the full body for signing"). No 5 MiB multipart, no counting-reader unknown-size. | `internal/s3_client.hpp:107-139` | Go `manager.Uploader`, 5 MiB parts, streamed size |
| **K-2** ✅ | MAJOR | **Content read fully buffers.** Per-segment `Range` headers are correct, but each body is copied into a full vector and all segments concatenated; `load_reader` is a facade that buffers then wraps in a `stringstream`. | `internal/s3_client.hpp:143-198`, `content.hpp:84-92` | Go lazy single-body Range reader |
| **K-3** ✅ | MAJOR | **No writer-callback save.** No sink/`saveFromWriter` anywhere; the only reader API buffers. | grep: none | Go/Java writer-callback save |
| **K-4** ✅ | MAJOR | **No streaming artifact fetch.** `streamArtifact`/`streamSharedArtifact`/`ArtifactStream` absent; `fetch()` reads whole body into a vector. | `artifact.hpp:435-436` | Go/Java `streamArtifact` |
| **K-5** ✅ | MAJOR | **Artifact publish buffers.** Public `publish_artifact`/`publish_shared_artifact` exist but copy the entire payload into an in-memory multipart item. | `artifact.hpp:343-347` | Go piped multipart publish |

> **Approach is already decided** in [`CPP-LARGE-FILE-STREAMING.md`](CPP-LARGE-FILE-STREAMING.md):
> keep the hand-rolled cpp-httplib client, **no `aws-sdk-cpp`**, and use S3 **multipart** uploads
> (each `UploadPart` is an ordinary full-body signed PUT ≤ 32 MiB, sidestepping chunked SigV4
> entirely) plus a content-receiver-backed streaming read. Backend-verified against s3proxy.
> **That spec covers K-1 and K-2 only** (content read/write; it deliberately keeps `content.hpp`
> signatures unchanged). It does **not** cover **K-3** (writer-callback save — a new additive API,
> and a prerequisite for the hello-world LoremIpsum streaming action H-1) or **K-4/K-5** (artifact
> streaming — same techniques applied to the artifact client, needs its own small design). Treat
> those two as explicit riders on top of the streaming spec.

### Results, events, actions (§3, §11)

| ID | Sev | Deficiency | Evidence | Reference |
|----|-----|-----------|----------|-----------|
| **K-6** ✅ | MAJOR | **Egress & Timed-Ingress cannot Filter.** `EgressResultType` / `IngressResultType` variants omit `FilterResult`; only `TransformResultType` includes it. Spec §3.1/§6-item-8 explicitly requires Filter on both (Java parity). | `result.hpp:539-541` | Java egress/ingress filter |
| **K-7** ✅ | MAJOR | **No result-level `add_metric` API.** The `Metric` wire field exists on `ActionEvent` but is unreachable by authors — `new_base_event` never populates it and no result exposes `add_metric`. | `event.hpp:103-164`, `result.hpp` | Go/Java `addMetric` |
| **K-8** ✅ | MAJOR | **No thrown error/filter disposition.** No `ErrorResultException`/`FilterResultException`; the worker's generic `catch` builds `ErrorResult(actx, ex.what())`, discarding context/annotations/metrics. | `worker.hpp:256-262` | Java thrown dispositions |
| **K-9** ✅ | MAJOR | **Error context has no stack trace.** Uncaught exceptions map to ERROR with `cause = what()` and empty `context`; spec §11.3 requires the full stack trace in `context`. | `worker.hpp:258-259`, `result.hpp:409` | Go/Java stack in context |
| **K-10** ✅ | MAJOR | **`insert`-based merges are first-wins (should be later-wins).** `default_join` metadata merge and `TransformResult::add_metadata(map)` both use `std::map::insert` (no overwrite). `ChildTransformResult`/`IngressResultItem`/all `add_annotations(map)` share the bug. The single-key `add_metadata(k,v)` uses `[]=` (last-wins) — so the two overloads are inconsistent. | `action.hpp:110`, `result.hpp:138,193,296` | Spec §3.1 later-wins |

### Lifecycle, transport, security (§1, §8, §13, §14)

| ID | Sev | Deficiency | Evidence | Reference |
|----|-----|-----------|----------|-----------|
| **K-11** | MAJOR | **Long-running-task reporting ignores the threshold.** Reports every in-flight task each heartbeat from the first cycle; the 30s threshold constant is defined but unused. Also the key form is `did:action`, not `clazz:action#thread:did`. Heartbeat cadence is a hardcoded 100×100ms loop; `heartbeat_interval=10s` is defined but unreferenced. | `heartbeat.hpp:22-23,51,114-157` | Go/Java report path |
| **K-12** | MAJOR | **No Valkey pool sized to Σ(workers).** All result publishes serialize through one `shared_conn_` under `shared_mu_`; connection count is `1 + #actions + suppliers + artifact`, independent of worker counts → publish bottleneck. | `queue.hpp:26,57-63,98`, `worker.hpp:272` | §13 pool sizing |
| **K-13** | MAJOR | **Registration does not fail-fast on 4xx.** ~4 attempts with 500ms/2s/5s backoff, but any non-2xx (incl. 4xx) is retried; spec §7 requires fail-fast on 4xx, retry 5xx/IO. | `registration.hpp:108-131` | Go/Java 4xx fail-fast |
| **K-14** | MAJOR | **No TLS/mTLS to core or Valkey.** `CERT_PATH`/`KEY_PATH`/`CA_CHAIN_PATH` unused; Valkey is plaintext TCP; core client honors `https://` scheme but configures no client cert/CA. **Orchestration-verified**: plugin containers get `/certs` mounted (`docker-compose.yml:165`) and Java/Go auto-detect certs → mTLS, absent → plaintext (`go .../httpclient.go:35-83`). So in any SSL-enabled deployment a C++ plugin cannot register. Required for parity; deferrable only if C++ never runs in an SSL-enabled DeltaFi. | `resp_client.hpp:329-341`, `registration.hpp:94-101` | Java/Go conditional mTLS |
| **K-15** | MAJOR | **30s drain deadline is soft.** SIGTERM/SIGINT handler + stop-request exist, but the deadline only decides *whether* to `join()`; `ActionWorker::join()` → `pool_.wait()` is unbounded, so a >30s action blocks shutdown indefinitely. Past-deadline workers are simply never joined. | `plugin.hpp:300-313`, `worker.hpp:202-205` | Go 30s hard drain |
| **K-14b** ✅ | MAJOR | **S3/MinIO TLS incomplete.** Transport TLS works when `MINIO_URL` has an `https://` scheme (`s3_client.hpp:84-95`), but there is **no CA-chain trust** (`set_ca_cert_path`), **no server-cert-verification config**, and **no client cert** — so it can't validate a private DeltaFi CA and can't do mTLS to MinIO. `MINIO_USE_SSL` is never read from env (scheme-inferred only). Default deployment is plaintext (`MINIO_URL=http://…:9000`), so this bites only in SSL-enabled deployments. | `s3_client.hpp:80-95`, `plugin.hpp:43,107-111,198` | Go `MinioUseSSL` + SDK CA |
| **K-16** | MAJOR | **`dependencies` hardcoded empty.** Field exists and serializes but `build_registration` sets `reg.dependencies = {}` with no config path. | `plugin.hpp:377`, `registration.hpp:58` | Java declarable deps |
| **K-17** ✅ | MAJOR | **Orphan GC gaps** (§5.5 correctness): runs for transform/join only — egress executor and ingress executor never call `cleanup_orphans`; `collect_result_segments(Ingress)` is a stub; cleanup is a trailing statement, not scope-guarded, so a transform that **throws or returns Error** after saving scratch content leaks. | `registry.hpp:60-122,223-247`, `result.hpp:602-616` | Go/Java GC-all + finally |

> K-17 resolved to MAJOR / Phase 1: the fixes touch the same result-layer files as K-6..K-10 and
> the meaningful leak (save-then-throw/error in a transform) is the cheapest to fix (scope guard).
> Egress writes no output content and ingress content is referenced, so those two skips rarely
> leak — but they're a few lines each and worth closing while we're in the file.

### Lookup & artifact completeness (§9, §10)

| ID | Sev | Deficiency | Evidence | Reference |
|----|-----|-----------|----------|-----------|
| **K-18** ✅ | MINOR | **Lookup cache lacks single-flight + background eviction**; expiry is lazy-on-access only. | `lookup.hpp:139-165` | §9.1 single-flight |
| **K-19** ✅ | MINOR | **Lookup admin ops absent** except `upload_table` — no create/upsert/remove/getTables/setBackingServiceActive. `LookupResults` has no `ttlSeconds`. | `lookup.hpp:168-184` | Java admin ops |
| **K-20** ✅ | MINOR | **Artifact gaps**: no `PLUGIN_FIRST` scope fallback; no poll-drift invalidation (push-only + lazy TTL); reconnect is a fixed 5s sleep, not backoff; no `seedIfMissing` (default-publish throws on 409 instead of tolerating); no `ArtifactManifest.isDefault()`; no per-callback preload/cacheable toggles. | `artifact.hpp:171-266,374-461` | Java/Go artifact client |

### Error text & logging (§11) — MINOR

| ID | Sev | Deficiency | Evidence |
|----|-----|-----------|----------|
| **K-21** ✅ | MINOR | Input-accessor error text diverges from canonical: `"missing required metadata key: {k}"` vs `"Missing metadata with key {k}"`; `"content index N out of range (have M)"` vs `"Action received no content"`/OOB. | `errors.hpp:15,28-30` |
| **K-22** ✅ | MINOR | `LogSeverity` has no `TRACE` (only Info/Warning/Error). Debug ostream emits `"WARN"` while wire emits `"WARNING"`. | `log.hpp:16-29`, `result.hpp:29` |
| **K-23** ✅ | MINOR | `FilterResult`/`FilterEvent` carry no `context` field (Error does). | `event.hpp:56-64`, `result.hpp:430-433` |
| **K-24** | MINOR | `ActionDescriptor` omits `docsMarkdown`; always emits `image`. | `registration.hpp` |
| **K-25** | MINOR | `/tmp/running` not removed on the exception-unwind shutdown path (no RAII guard). | `plugin.hpp:321-329` |

### Test framework (§12)

| ID | Sev | Deficiency | Evidence | Reference |
|----|-----|-----------|----------|-----------|
| **K-26** ✅ | MAJOR | **Runners bypass the production dispatch path.** `run_transform`/`run_egress`/`run_ingress` call the action method directly with a caller-built input; they skip `build_transform_input` (QueueEvent→typed input), skip params deserialization from `action_params`, and never run `cleanup_orphans` or the fault barrier. Params are default-constructed, not bound. | `testing.hpp:309-355` vs `registry.hpp:70-77,222-248` | Go runners hit real dispatch |
| **K-27** ✅ | MAJOR | **No `run_join`.** No join→transform→event runner; the subtle merge→transform handoff is untestable through the framework. | grep: none | (all kits weak here) |
| **K-28** ✅ | MAJOR | **No fault-injection hook** (`failAfterNth`) on the in-memory store; error paths untestable. | `testing.hpp:24-110` | Go store hook |
| **K-29** ✅ | MINOR | Assertions are type-level only (`require_transform/error/filter/egress/ingress`); **no** content/metadata/annotation/message/metric/`require_single_transform` helpers. | `testing.hpp:256-300` | Go/Java assertions |
| **K-30** ✅ | MINOR | No typed-input builders (`new_deltafile`/`new_timed_ingress_deltafile`); no context-swap wiring for mock lookup/artifact clients; no TSan/race target. Shipped suite covers only crypto/params/resp — nothing exercises worker/dispatch/join/content/streaming or the framework itself. | `testing.hpp`, `tests/CMakeLists.txt:13-15` | Go `-race` acceptance |

**In-memory store, mocks, context factory are solid** (`testing.hpp:24-237`, real multi-segment
range load; `MockLookupClient`/`MockArtifactClient` with call recording) — the gap is the dispatch
path, `run_join`, the fault hook, and richer assertions.

---

## Part 2 — Hello-world plugin deficiencies (`deltafi-cpp-hello-world`)

The Java/Go reference plugins are separate repos and absent here; audited against Appendix B text.
Several gaps are spec-acknowledged (`ACTION_KIT_DESIGN.md:1131`: C++ "omits streaming, has no own
CI, bakes coordinates into a macro"). This plugin now lives in its own repository at
[`gitlab.com/deltafi/plugins/deltafi-cpp-hello-world`](https://gitlab.com/deltafi/plugins/deltafi-cpp-hello-world);
the file references below (e.g. `main.cpp`) are relative to that repo's root.

| ID | Sev | Deficiency | Evidence |
|----|-----|-----------|----------|
| **H-1** ✅ | MAJOR | **Streaming pair missing** (LoremIpsum + Capitalize actions, the `streaming` flow `DeleteContent→LoremIpsum→Capitalize`, and the `loremIpsumSize` variable). Required for a full port. Depends on kit K-1..K-3. | no such actions/flow; `variables.json` |
| **H-2** ✅ | MAJOR | **Egress does not stream.** Buffers via `content.load_string()` then `data.find("error")`; no read-boundary straddle handling. Depends on K-2. | `actions/egress.hpp:19-34` |
| **H-3** ✅ | MAJOR | **Shared data files missing.** No `lookup-tables/cities.json` or `lookup-tables/f1-drivers.csv`; CSV/JSON suppliers hard-code rows in `.hpp` instead of serving the byte-identical shared resources. | `suppliers/csv_lookup_supplier.hpp:22-29`, `json_lookup_supplier.hpp:22-26` |
| **H-4** ✅ | MAJOR | **No CI.** No `.gitlab-ci.yml`/`.github`; the two required integration tests (`join-group-test`, `transform-many-test`) are not wired to run against a live deployment. Spec-acknowledged; blocks "definition of done." | absent |
| **H-5** ✅ | MINOR | **`SKIP_TABLE_LOOKUP` env-var toggle missing** in Transform (lookups gated only on client presence). | `actions/transform.hpp:111` |
| **H-6** ✅ | MINOR | **Artifact divergence**: Transform reads `metadata.json`; spec ties the `config.yaml` watch/onUpdate to the transform action. `config.yaml` is watched only at plugin level with a bare log callback. | `actions/transform.hpp:26-37`, `main.cpp:31-44` |
| **H-7** ✅ | MINOR | **Apache `HEADER` only on `Makefile`**, not on `.hpp`/`.cpp`/`Dockerfile`/`CMakeLists.txt` (they use `ABOUTME:` comments). | source tree |
| **H-8** ✅ | MINOR | **`groupId` diverges**: macro uses `org.deltafi` (with `org.deltafi.cpp-hello-world` only as the action-type prefix); spec §B.7 says `groupId = org.deltafi.cpp-hello-world`. Internally consistent with the integration-test plugin refs. | `main.cpp:47-50` |

**Solid in the plugin:** all six non-streaming action behaviors (transform/split/join/timed-ingress
match the contract), all 4 suppliers, the full `flows/` topology incl. the join block, all 3
integration JSONs, and thorough unit tests on the kit framework for every existing action.

---

## Part 3 — Parity plan (phased)

Ordering is by dependency and value. Run `fix`/`fmt`/build+tests after each phase.
`hello-world` items that depend on kit items are placed after their dependency.

### Phase 1 — API correctness (low-risk, high-value; unblocks Filter/metrics/errors)
Pure additions and small fixes to the result/event/worker layer. No HTTP rework.
- **K-6** Add `FilterResult` to `EgressResultType` and `IngressResultType`; wire the FILTER
  mapping in the egress/ingress executors.
- **K-7** Add `add_metric` to the result base and populate `ActionEvent.metrics` in `new_base_event`.
- **K-8** Add `ErrorResultException`/`FilterResultException`; convert them in `Worker::process`
  before the generic catch, copying cause/context/annotations/metrics.
- **K-9** Capture a stack trace into the error `context` on uncaught exceptions (libunwind/
  `std::stacktrace` where the toolchain allows).
- **K-10** Change the `insert`-based merges to overwrite (later-wins) in `default_join`,
  `TransformResult::add_metadata(map)`, `ChildTransformResult`, `IngressResultItem`, and
  `add_annotations(map)`.
- **K-17** Run orphan GC for egress/ingress; implement `collect_result_segments(Ingress)`;
  scope-guard cleanup so thrown actions still GC.
- **K-21..K-23** Canonical error text; add `TRACE`; add `context` to Filter.

### Phase 2 — Lifecycle & transport hardening
- **K-13** Registration: fail-fast on 4xx, retry only 5xx/IO.
- **K-15** Enforce a hard 30s drain (bounded `wait_for`, then detach/abandon remaining workers).
- **K-11** Gate long-running-task reporting on the 30s threshold; fix the key form; use the
  `heartbeat_interval` constant.
- **K-12** Size the Valkey connection pool to Σ(per-action workers) + housekeeping; give workers
  their own publish connections.
- **K-16** Make `dependencies` declarable (from build metadata / env / registration API).
- **K-14** mTLS to core (and Valkey where core requires it): honor `CERT_PATH`/`KEY_PATH`/
  `CA_CHAIN_PATH`, plaintext fallback when absent.
- **K-14b** S3/MinIO TLS: read `MINIO_USE_SSL`, and wire `CA_CHAIN_PATH` (`/certs/ca.crt`) into the
  S3 httplib client (`set_ca_cert_path` + server-cert verification) plus the client cert for mTLS.
  Touches `s3_client.hpp` — **coordinate with Phase 3**, which rewrites the same file for
  streaming; sequence them to avoid double-editing the client (do K-14b as part of the Phase 3
  s3_client pass, or land it first and rebase streaming on top).
- **K-25** RAII-remove `/tmp/running` on all shutdown paths.

### Phase 3 — Streaming (the large item)
- **K-1/K-2** — execute [`CPP-LARGE-FILE-STREAMING.md`](CPP-LARGE-FILE-STREAMING.md) as written:
  multipart upload (8 MiB parts, ≤ 32 MiB ceiling, counting-reader unknown-size, abort-on-failure)
  and a content-receiver-backed bounded `loadReader`. Hand-rolled client, no `aws-sdk-cpp`, no
  chunked SigV4. `content.hpp` signatures unchanged.
- **K-3** (rider, not in that spec) — add a writer-callback save (`saveFromWriter`) on the
  result/context surface; prerequisite for hello-world H-1.
- **K-4/K-5** (rider, not in that spec) — apply the same read/multipart techniques to the artifact
  client: `streamArtifact`/`streamSharedArtifact` fetch + piped multipart publish. Needs a short
  design of its own.

### Phase 4 — Lookup & artifact completeness
- **K-18** Lookup single-flight + background eviction.
- **K-19** Lookup admin ops + `ttlSeconds`.
- **K-20** Artifact `PLUGIN_FIRST`, poll-drift invalidation, backoff reconnect, `seedIfMissing`
  409-tolerance, `isDefault()`, preload/cacheable toggles.

### Phase 5 — Test framework
- **K-26** Route `run_*` through the real registry executors (build input from a QueueEvent,
  deserialize params, run orphan GC + fault barrier).
- **K-27** Add `run_join` (join→transform→event).
- **K-28** Fault-injection hook (`failAfterNth`) on the in-memory store.
- **K-29/K-30** Content/metadata/annotation/message/metric + `require_single_transform`
  assertions; typed-input builders; context-swap wiring for mocks; a TSan/race target; and a kit
  self-test suite that actually exercises worker/dispatch/join/content/streaming.

### Phase 6 — Hello-world plugin parity
- **H-3** Add shared `cities.json`/`f1-drivers.csv`; serve suppliers from resources.
- **H-5** `SKIP_TABLE_LOOKUP` env-var toggle.
- **H-6** Align the artifact watch to `config.yaml` on the transform action.
- **H-2** Streaming egress (after K-2) with straddle handling.
- **H-1** Streaming pair + `streaming` flow + `loremIpsumSize` (after K-1..K-3).
- **H-4** CI wiring (build + unit test + publish + dev-install + run the two integration tests).
- **H-7/H-8** Apache headers on all sources; reconcile `groupId` with the spec.

---

## Decisions — resolved

1. **Streaming HTTP stack (K-1..K-5).** RESOLVED — adopt `CPP-LARGE-FILE-STREAMING.md` verbatim
   for K-1/K-2 (multipart write + content-receiver read over the hand-rolled client; no
   `aws-sdk-cpp`; no chunked SigV4). K-3 (writer-callback save) and K-4/K-5 (artifact streaming)
   are explicit riders not covered by that spec.
2. **K-17 severity.** RESOLVED — MAJOR, folded into Phase 1 (same files as the result-layer work;
   the save-then-throw/error leak is the cheapest and most meaningful fix).
3. **TLS (K-14 + K-14b).** RESOLVED — required for parity across **all three** channels: core
   (httplib), Valkey (RESP/asio; asio-ssl already vendored), and S3/MinIO (httplib). Orchestration
   verified: plugin containers mount `/certs`; Java/Go auto-detect (certs present → mTLS, absent →
   plaintext). A C++ plugin that ignores `/certs` cannot register — and cannot reach MinIO — in an
   SSL-enabled deployment. Implement the same conditional auto-detect in Phase 2 (both-or-neither
   fallback), wiring `CA_CHAIN_PATH` into the S3 client too (K-14b). Deferrable only if C++ plugins
   will never run in an SSL-enabled DeltaFi — not assumed here.
4. **Hello-world repo location.** RESOLVED — the plugin stays in this repo until the parity work is
   complete. Therefore **all** H-items land here, including **H-4 (CI wiring)** and the
   packaging/header/`groupId` cleanup — wiring the two integration tests into CI here is the
   spec's "definition of done" and is not deferred to the future repo split. The hardcoded
   `LOCAL_ACTION_KIT` guard remains intentional until the eventual split.
