# DeltaFi Action Kit — Conformance Matrix

Side-by-side conformance of the four action kits against
[`ACTION_KIT_DESIGN.md`](ACTION_KIT_DESIGN.md). Derived from a code-level audit of each kit
(Java `deltafi-action-kit`, Go `deltafi-go-action-kit`, C++ `deltafi-cpp-action-kit`, Python
`deltafi-python`).

## Legend

| Mark | Meaning |
|------|---------|
| ✅ | Conformant — meets the spec requirement. |
| ⚠️ | Partial — works but diverges, incomplete, or not hardened to the stated bar. |
| ❌ | Lapse — required behavior missing or violated. |
| 🔧 | Not a kit fault — the **spec** overstates/mis-states; see §"Spec Corrections". |
| N/A | Not applicable in that language (idiomatic non-issue). |

**Severity** (for the lapse lists): **BLOCKER** breaks wire conformance/interop · **MAJOR**
missing required feature/mandate · **MINOR** cosmetic/idiomatic/doc.

> **No BLOCKERs were found in any kit.** All four are wire-conformant: queue protocol, object-key
> layout, registration endpoint, and the core event schemas all interoperate with DeltaFi core.
> Every lapse below is MAJOR or MINOR.

---

## 1. Cross-Cutting Mandates (the three emphasized requirements)

| Mandate | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| **Content streaming** — bounded memory, 5 MiB multipart, server-side Range, unknown-size, writer-callback (§5.4) | ✅ | ✅ | ❌ | ❌ |
| **Artifact streaming** — `streamArtifact` fetch + piped publish (§10) | ✅ | ✅ | ❌ | ⚠️ |
| **Unit-test framework** — production-dispatch runners, in-memory store w/ fault hook, assertions, no data-path mocks (§12) | ⚠️ | ⚠️ | ⚠️ | ⚠️ |
| **Idiomatic implementation** (§15) | ✅ | ✅ | ✅ | ✅ |

- **C++ / Python content**: both fully buffer. C++ sends per-segment `Range` headers on read but then buffers the whole body; its SigV4-over-httplib write path and `load_reader` buffer too. Python ranges reads but `b"".join(...read())` buffers, and `put_bytes` wraps full `bytes` in `BytesIO`. Neither has `saveFromReader`/`loadReader`/writer-callback.
- **Python artifacts**: fetch streaming (`ArtifactStream`) works; **publish** buffers and there is **no public `publishArtifact`/`publishSharedArtifact`** (only internal `seed_if_missing`).
- **Test framework**: *every* kit is partial — see the dedicated breakdown in §3.

---

## 2. Master Conformance Matrix (by spec dimension)

### Lifecycle & Worker Model (§1, §2)

| Requirement | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| Register before consuming work (§1.4) | ✅ | ✅ | ✅ | ❌ starts workers first |
| One-poller / N-workers per action (§2) | ✅ | ✅ | ✅ | ✅ |
| Poller uses ~5s timeout loop (responsive shutdown) (§8) | ❌ `BZPOPMIN(0)` | ✅ 5s | ✅ 5s | ❌ `bzpopmin(0)` |
| Worker-count precedence: override > `ACTIONS_ACTIONTHREADS_{NAME}` > `ACTION_THREADS` > 1 (§2) | ⚠️ Spring map only; global var inert | ✅ | ✅ | ❌ `thread_config` only |
| Fault barrier (action never crashes worker) (§2, §11.3) | ✅ | ✅ | ✅ | ✅ |
| Graceful 30s drain on SIGTERM/SIGINT (§1.10, §16) | ⚠️ ~5s then force | ✅ 30s | ⚠️ deadline soft; `join()` unbounded | ❌ no handler/drain |
| `/tmp/running` readiness marker (§1.9) | ✅ | ✅ | ✅ | ⚠️ created, never removed |

### Queue Protocol & Serialization (§8)

| Requirement | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| ZSET + `BZPOPMIN` intake on action-named queue | ✅ | ✅ | ✅ | ✅ |
| Poll-loop error backoff: capped exponential + jitter (100ms→5s), reset on read (§8) | ✅ | ✅ | ❌ | ✅ |
| `ZADD` epoch-ms publish to `dgs` / `dgs-{returnAddress}` | ✅ | ✅ | ✅ | ✅ |
| Heartbeat + long-running-task hashes (correct keys) | ✅ | ✅ | ✅ | ✅ |
| Report in-flight tasks w/ startTime (threshold is core-side, §8 relaxed) | ✅ | ✅ | ✅ | ✅ |
| camelCase field names | ✅ | ✅ | ✅ | ✅ |
| Null-tolerant serialization (§8 relaxed) | ✅ | ✅ | ✅ | ✅ |
| Timestamps RFC3339 (offset/`Z`) **or** epoch (§8 relaxed) | ✅ offset | ✅ offset | ✅ µs `Z` | ✅ epoch |
| Tolerate unknown incoming fields | ✅ | ✅ | ✅ | ✅ |

### Content & Storage (§5)

| Requirement | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| Object-key layout `did[:3]/did/uuid` | ✅ | ✅ | ✅ | ✅ |
| Zero-copy slicing/composition (subcontent/append/prepend) | ✅ | ✅ | ✅ | ✅ |
| Streaming upload (5 MiB parts, unknown size) | ✅ | ✅ | ❌ | ❌ |
| Streaming read (`loadReader`, server-side Range) | ✅ | ✅ | ❌ buffers body | ⚠️ ranged but buffered |
| Writer-callback save | ✅ | ✅ | ❌ | ❌ |
| Orphan-content GC after execution (§5.5) | ✅ | ✅ | ⚠️ transform only; leaks on egress/ingress + thrown | ✅ |

### Actions, Results, Inputs (§3, §4)

| Requirement | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| Transform / Egress / TimedIngress action types | ✅ | ✅ | ✅ | ✅ |
| Join (merge → transform), default concat/merge | ✅ | ⚠️ no default merge (author supplies) | ⚠️ default merges **first-wins**, spec says later-wins | ✅ |
| Result union: Transform/Transforms/Egress/Ingress/Error/Filter | ✅ | ✅ | ❌ Egress/Ingress **cannot** Filter | ❌ Ingress cannot Filter |
| Result `addMetric` (author API) (§11.2) | ✅ | ✅ | ❌ no API (wire field unreachable) | ✅ |
| Thrown error/filter disposition (§3.4) | ✅ | N/A (returns state) | ❌ generic only; drops context/annotations | ⚠️ generic only (typed input excs map) |
| Error context carries stack trace (§11.3) | ✅ | ✅ | ❌ `what()` only | ✅ traceback |
| Input accessors + typed `MissingMetadata`/`ExpectedContent` errors | ⚠️ `ExpectedContent` path dead | ✅ | ⚠️ message text diverges | ✅ |

### Parameters & Schema (§6)

| Requirement | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| Typed params + map binding | ✅ | ✅ | ✅ | ✅ |
| String→type coercion (substituted vars) | ✅ | ✅ | ✅ | ✅ (pydantic) |
| JSON Schema: camelCase, `additionalProperties:false`, inline `$ref` | ✅ | ✅ | ✅ | ❌ pydantic verbatim (snake_case, `$defs`, no `additionalProperties`) |
| Honor description/default/enum/required/min/max | ⚠️ no `minimum`/`maximum` | ✅ | ✅ | ⚠️ as pydantic emits |
| `EnvVar` (name-only, `x-deltafi-type`, runtime resolve) | ✅ | ✅ | ✅ | ❌ absent |
| `DataSize` (units, pattern, `x-deltafi-type`) | ⚠️ missing `x-deltafi-type` tag | ✅ | ✅ | ❌ absent |

### Registration (§7)

| Requirement | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| `POST {CORE_URL}/plugins` with full payload | ✅ | ✅ | ✅ | ✅ |
| Retry with backoff (~4 attempts) | ⚠️ 3 attempts, diff backoffs | ✅ | ✅ | ❌ single POST, no retry/timeout |
| **Fail-fast on 4xx**, retry 5xx/IO | ✅ | ✅ | ❌ retries all non-2xx | ❌ exit(1) on any failure |
| `dependencies` declarable | ✅ | ❌ hardcoded `[]` | ⚠️ | ❌ hardcoded `[]` |

### Lookup Tables (§9)

| Requirement | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| `lookup()` query (GraphQL) | ✅ | ✅ | ✅ | ✅ |
| In-process cache + single-flight + eviction | ✅ (TTL via core) | ✅ | ⚠️ cache, no single-flight/eviction | ❌ none |
| Admin ops (create/upsert/remove/setBackingActive/upload) | ✅ | ⚠️ only internal `UploadTable` | ❌ only `lookup` | ❌ only `lookup` |
| Suppliers (runner on `lookup-table-event-{name}`) | ✅ | ✅ | ✅ | ✅ |
| `LookupResults` full shape (created/modified/ttlSeconds) | ✅ | ✅ | ⚠️ no `ttlSeconds` | ❌ totalCount+results only |

### Artifacts (§10)

| Requirement | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| Fetch (buffered) + manifest from `X-Artifact-*` headers | ✅ | ✅ | ✅ | ✅ |
| Streaming fetch (`streamArtifact`) | ✅ | ✅ | ❌ | ✅ |
| Publish (streamed multipart) | ✅ | ✅ | ❌ buffers | ⚠️ buffers; no public publish |
| Scopes incl. `PLUGIN_FIRST` fallback | ✅ | ❌ separate methods only | ❌ separate methods only | ✅ |
| Push invalidation (pub/sub `deltafi:artifact:version-changed`) | ✅ | ✅ | ✅ | ✅ |
| Poll-drift invalidation (~60s) | ✅ | ❌ TTL evict only | ❌ | ✅ |
| `seedIfMissing` tolerates 409 race | ✅ | ❌ 409 is hard error | ⚠️ | ✅ |
| `ArtifactManifest.isDefault()` | ✅ | ❌ | ❌ | ✅ |

### Observability & Errors (§11)

| Requirement | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| Operational logging | ✅ | ✅ | ✅ (spdlog) | ✅ |
| Result-attached `LogMessage` (info/warn/error) | ✅ | ✅ | ✅ | ✅ |
| `LogSeverity` TRACE/INFO/WARNING/ERROR | ✅ | ⚠️ INFO/WARN/ERROR | ⚠️ no TRACE | ✅ (+USER extra) |
| Canonical error mapping (§11.3) | ✅ | ✅ | ⚠️ loses context on throw | ✅ |

### Configuration & Security (§13, §14)

| Requirement | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| Canonical env vars (CORE_URL/VALKEY/MINIO/etc.) | ✅ | ✅ | ✅ | ✅ |
| Cache-TTL env vars wired | ⚠️ not read | ✅ | ✅ | ❌ not read |
| Mutual TLS to core (cert/key present → mTLS) | ✅ | ✅ | ❌ none | ❌ none |
| Valkey pool sized for Σ(workers)+overhead | ✅ | ⚠️ default pool | ✅ | ✅ |
| `EnvVar` secret indirection (§14) | ✅ | ✅ | ✅ | ❌ (no EnvVar) |
| Admin headers consistent kit identity | ⚠️ two fixed names | ✅ | ✅ | ⚠️ two fixed names |

---

## 3. Unit-Test Framework Breakdown (§12) — all kits partial

| Capability | Java | Go | C++ | Python |
|---|:--:|:--:|:--:|:--:|
| In-memory content store (real save/load/range) | ⚠️ no multi-seg fault hook | ✅ | ⚠️ no fault hook | ❌ single-segment, TODO |
| Fault-injection hook (`failAfterNth`) | ❌ | ✅ | ❌ | ❌ |
| Runners exercise **production dispatch** path | ❌ direct method call | ✅ (except join) | ❌ bypass registry executor | ✅ via `execute_action` |
| `runJoin` (join → transform → event) | ❌ | ❌ Join only, no transform/event | ❌ | ✅ (`join_meta`) |
| Result/event assertions (require*) | ✅ AssertJ | ✅ | ⚠️ minimal | ⚠️ Result-shaped, no require* |
| Mock lookup + artifact clients w/ recording | ⚠️ artifact only | ✅ both | ⚠️ both, no ctx swap | ⚠️ artifact only |
| Race/TSan target | ⚠️ | ✅ `-race` | ❌ | ⚠️ |

---

## 4. Per-Language Lapse Summary

### Java — wire-perfect; gaps in shutdown & test harness

**MAJOR**
1. Graceful drain is ~5s then force-interrupt, not 30s (`ActionRunner.close`); poller blocks on `BZPOPMIN(0)` and won't wake to drain.
2. Test framework has no production-dispatch runners (`DeltaFiTestRunner` calls the action method directly, bypassing `executeAction`); only the deprecated `ActionTest` drives the real path; no `runJoin`.
3. In-memory store has no fault-injection hook.
4. Global `ACTION_THREADS` is inert; only the Spring `actionThreads` map is honored; documented name normalization not implemented.

**MINOR** — `DataSize` schema missing `x-deltafi-type`; `minimum`/`maximum` not emitted; registration 3 attempts (not ~4) with different backoffs; `ExpectedContentException` mapping path is dead (OOB throws `ActionKitException` instead); no shipped `MockLookupClient`; cache-TTL env vars not read; long-running tasks reported every cycle; admin `X-User-Name` is a fixed string.

### Go — the streaming reference; smallest gap set

**MAJOR**
1. `RunJoin` test helper only calls `Join()` and returns a `Deltafile` — never runs the subsequent transform nor produces an `ActionEvent`, so the subtle join→transform handoff can't be tested through the framework.
2. `LookupClient` exposes only `lookup`; admin ops (getTables/create/upsert/remove/setBackingActive) absent (additive per §9.1, so borderline).

**MINOR** — timestamps RFC3339Nano with **local offset** (no `.UTC()`); no artifact **poll-drift** invalidation; no `PLUGIN_FIRST` scope; `seedIfMissing` 409 is a hard error; no `ArtifactManifest.IsDefault()`; `docsMarkdown` not emitted + extra `description`; `dependencies` hardcoded empty; timed-ingress error/filter events drop annotations; no default join merge; Valkey pool not explicitly sized; stale "reflection" doc comment in `action.go`.

### C++ — wire-conformant; two mandates unmet + API holes

**MAJOR**
1. Content path fully buffers (no 5 MiB multipart, no bounded `loadReader`, no writer-callback save, no streaming save on results).
2. No artifact streaming fetch (`streamArtifact`/`ArtifactStream` absent); publish buffers.
3. No result-level `add_metric` — the `Metric` wire field is unreachable by authors.
4. `EgressResultType`/`IngressResultType` omit `FilterResult` — egress & timed-ingress structurally cannot filter. **Confirmed required** (Java supports it); add `FilterResult` to both variants.
5. Orphan GC runs for transforms only; egress/ingress skip it and `collect_result_segments(Ingress)` is a stub → leaks; thrown transform also leaks (cleanup not in a finally).
6. Registration does not fail-fast on 4xx.
7. No TLS/mTLS to core (no `CERT_PATH`/`KEY_PATH`/`CA_CHAIN_PATH`).
8. No thrown error/filter disposition; generic exception → `ErrorResult(ex.what())` discards context/annotations/metrics.
9. `default_join` merges metadata **first-wins** (spec: later-wins); `TransformResult.add_metadata(map)` same bug.
10. Test framework: no `runJoin`, no fault hook, runners bypass the registry executor (skip orphan-GC/fault barrier), minimal assertions, shipped suite has no worker/dispatch/join/content tests.

**MINOR** — `LogSeverity` has no `TRACE`; long-running threshold constant unused + key form `did:action`; 30s drain not hard-enforced; `FilterResult`/`FilterEvent` carry no `context`; input-accessor error messages diverge from canonical text; `ActionDescriptor` omits `docsMarkdown`, always emits `image`; lookup client lacks single-flight/eviction/`ttlSeconds`/admin ops; artifact client lacks poll/single-flight/`PLUGIN_FIRST`/`isDefault`.

### Python — production kit; most lapses

**MAJOR**
1. Content path fully buffers; no `saveFromReader`/`loadReader`/writer-callback.
2. Artifact publish buffers; no public `publishArtifact`/`publishSharedArtifact`.
3. `EnvVar` and `DataSize` parameter types entirely absent (and the §14 secret-indirection contract with them).
4. Worker-count env vars (`ACTIONS_ACTIONTHREADS_{NAME}`, `ACTION_THREADS`) ignored.
5. No graceful shutdown: no SIGTERM/SIGINT handler, no 30s drain, `/tmp/running` never removed; workers block on `bzpopmin(name, 0)`.
6. Registration: single POST, no retry/backoff/timeout, `exit(1)` on any failure (incl. 5xx/transient), and **workers start before registration**.
7. JSON-schema generation uses pydantic verbatim — no `additionalProperties:false`, `$defs`/`$ref` not inlined, snake_case (not camelCase).
8. Lookup client: no cache/single-flight/TTL; thin `LookupResults` (no created/modified/ttlSeconds); missing admin ops.
9. No TLS/mTLS to core.
10. Test in-memory store is single-segment (`# TODO`), no real range/multi-segment, no fault hook.
11. Timed-ingress `valid_result_types` is `(IngressResult, ErrorResult)` — **cannot return `FilterResult`**. Confirmed required (Java supports it); add `FilterResult`.

**MINOR** — long-running threshold never applied; no thrown error/filter disposition; artifact cache has no TTL (`ARTIFACT_CACHE_TTL` unused); `Metric` lacks `addTag`/STATSD helper; `LogSeverity.USER` extra; `APP_NAME` no hostname fallback; `dependencies` hardcoded empty; test runners assert on `Result` not `ActionEvent` and lack named `run*`/`require*` helpers; no `MockLookupClient`; post-registration initial lookup upload unverified.

---

## 5. Scorecard

Counts are MAJOR / MINOR lapses (excluding 🔧 spec-fault items and idiomatic non-issues).

| Kit | BLOCKER | MAJOR | MINOR | Streaming | Test FW | Overall |
|---|:--:|:--:|:--:|:--:|:--:|---|
| **Go** | 0 | 2 | ~11 | ✅✅ | ⚠️ | **Strongest** — reference-grade; fix `RunJoin` + lookup admin ops. |
| **Java** | 0 | 4 | ~8 | ✅✅ | ⚠️ | Wire-perfect; harden shutdown + test runners. |
| **C++** | 0 | 10 | ~9 | ❌❌ | ⚠️ | Solid wire layer; needs streaming, metrics API, Filter on egress/ingress, TLS. |
| **Python** | 0 | 11 | ~9 | ❌⚠️ | ⚠️ | Most work: streaming, EnvVar/DataSize, drain, registration retry, schema, TLS, Filter-on-ingress. |

**Common to all four** (fix once, everywhere): every kit's test framework is partial;
`dependencies` is effectively unsupported except in Java. (The long-running-task threshold is no
longer a kit lapse — the spec was relaxed so the threshold is a core/UI concern; see §6.)

---

## 6. Spec Corrections — APPLIED to `ACTION_KIT_DESIGN.md`

The audits surfaced ten places where the spec, not the kits, was at fault. **Items 1–7, 9, 10 have
been applied (the spec was relaxed). Item 8 was resolved in the opposite direction — the spec stays
strict, making C++/Python lapses.**

1. ✅ **Timestamps (§0, §8, A.7).** Relaxed to: "RFC3339 with sub-second precision (offset or `Z`)
   **or** numeric epoch; core accepts both." (Java/Go emit offset, C++ `Z`, Python epoch — all
   valid.) Recommendation to normalize Go/Java to `.UTC()` for canonical output stands but is not a
   conformance requirement.
2. ✅ **Omit-null (§0, §8).** Relaxed to "null-tolerant; omitting nulls preferred but not required."
3. ✅ **Long-running-task ~30s threshold (§8).** Reworded: the kit reports in-flight tasks with
   `startTime`; **the threshold is a core/UI concern**. No kit is dinged for this anymore.
4. ✅ **`Metric` STATSD/`addTag` validation (A.9).** Marked Java/core-internal; required surface is
   the `{name, value, tags}` wire field + result-level `addMetric`.
5. ✅ **`ActionContext.save*` (A.2).** Noted both placements conformant (Go on context; Java/Python/
   C++ on results/content).
6. ✅ **`ZADD NX` (§8, A.12).** Scoped `NX` to input enqueues only; result/reply puts use plain
   `ZADD`.
7. ✅ **`ActionDescriptor.description` (§7, A.11).** Added the deprecated `description` field to the
   documented descriptor shape.
8. ❗ **Filter on Egress/Ingress (§3.1, A.1) — NOT relaxed.** Per decision, filtering from egress
   and timed-ingress **is required, exactly like Java.** The spec is correct as written. This flips
   C++ (MAJOR #4) and Python (new MAJOR #11) into real lapses: both must add `FilterResult` to their
   egress/ingress result unions.
9. ✅ **§5.4 C++ "only ranges on read."** Reworded: C++ sends per-segment `Range` headers but then
   buffers the whole body; Python ranges reads but joins all bytes in memory.
10. ✅ **(from §1 relaxations) Drain/poller wording** unchanged — 30s drain + ~5s poll loop remain
    required (Java/C++/Python lapses there stand).
