# Work Item: C++ Action Kit — Large-File Content Streaming

## Summary

The C++ action kit's S3 content client (`deltafi-cpp-action-kit`) fully materializes
every object in memory: reads buffer the whole object before returning a "stream", and
writes buffer the whole object for a single signed PUT with no multipart. This blocks
large-file content through C++ actions and is the one place the C++ client lacks parity
with the Java and Go clients. This item adds **genuine streaming reads** and **multipart
writes** to the hand-rolled client — **without** adding `aws-sdk-cpp`.

This is the separate ticket referenced by
[`MINIO-CLIENT-MIGRATION.md`](MINIO-CLIENT-MIGRATION.md) (decision **D4**). It is
independent of the MinIO→AWS-SDK dependency swaps (the C++ client has no MinIO
dependency) and can proceed on its own schedule.

- **Target release:** 2.x (behavior-only; public C++ API signatures unchanged → no plugin
  source breakage).
- **Mechanism:** multipart upload (decision **D3**, confirmed viable by the migration
  spec's Phase 0).
- **Do not add `aws-sdk-cpp`** — it would dwarf this minimal header-only action kit.

## Background

`deltafi-cpp-action-kit/include/deltafi/internal/s3_client.hpp` is a minimal,
header-only S3 client implementing AWS Signature V4 over cpp-httplib. It already does the
right thing for **reads with offsets** (server-side HTTP `Range`). Its gaps are in
streaming and large writes.

### Gap 1 — `load_reader` doesn't stream (buffers the whole object)

`ContentService::load_reader` (`content.hpp:84-92`) calls `load()`, which reads the
entire concatenated content into a `std::vector<uint8_t>` (`s3_client.hpp:143`, via
cpp-httplib's `res->body`), then **copies it again** into a `std::stringstream`. The
returned `std::istream` is fully resident in memory — transiently two copies — before the
caller reads a byte. (Go's `LoadReader` returns a lazy reader over the socket; Java's
`getObject` returns a socket-backed `ResponseInputStream`.)

### Gap 2 — `save_from_reader` doesn't stream and has no multipart

`S3Client::save_from_reader` (`s3_client.hpp:124-140`) reads the entire `istream` into a
`std::vector`, then `save()` sends it as a single signed PUT. The comment names the
cause: *"cpp-httplib requires the full body for signing and upload."* AWS SigV4 computes
`x-amz-content-sha256` over the full payload, so a single signed PUT inherently needs the
whole body materialized. Consequence: a C++ action saving multi-GB content holds it all
in RAM.

## Constraints from backend verification

The migration spec's Phase 0 spike (run against the live `deltafi-s3proxy`,
`filesystem` backend) established facts this ticket must honor:

- **Multipart works** on the s3proxy `filesystem` backend (create / upload / complete /
  assemble / part-boundary Range all correct). Completed objects are stored as a single
  plain file at the key path — the fast-delete daemon handles them like any object.
- **Single PUT > 32 MiB is rejected** with `MaxMessageLengthExceeded`
  (`S3PROXY_V4_MAX_NON_CHUNKED_REQ_SIZE=33554432`). Therefore **each `UploadPart` (and any
  fallback single PUT) must stay ≤ 32 MiB**; use a part size of 5–16 MiB.
- **Incomplete uploads** stage as `.mpus-<uploadId>-<key>-<part>` files in the key's
  directory. They are reapable via `list_multipart_uploads` + `abort`; this client must
  `AbortMultipartUpload` on any mid-upload failure so it never leaks staging on graceful
  errors.
- **Empty objects + Range** return HTTP **416 `InvalidRange`** — preserve the existing
  guard and treat 416 as empty.

## Goals / Non-Goals

**Goals**
- `load_reader` returns a streaming `std::istream` that pulls from the socket without
  buffering the whole object.
- `save`/`save_from_reader` upload arbitrarily large content via multipart, bounded
  memory, with abort-on-failure and a correct returned byte count.
- Keep the public C++ content API (`content.hpp`) signatures unchanged.
- No new third-party dependency (reuse `crypto.hpp`, cpp-httplib, spdlog).

**Non-Goals**
- Adding `aws-sdk-cpp`.
- Retry/backoff (not present today; out of scope — note as a future item).
- Concurrent part uploads (a single `httplib::Client` serializes; parallelism is a
  possible later optimization, not required for correctness).
- Eliminating unknown-size saves at the API level (that is the 3.0 action-kit API trim
  tracked in the migration spec, not here).

## Design

### Streaming reads (`load` / `load_reader`)

- Replace the buffering `client_->Get(path, headers)` (which fills `res->body`) with
  cpp-httplib's **content-receiver** overload
  `Get(path, headers, ContentReceiver receiver)`, which delivers the body in chunks via
  callback as they arrive.
- Back `load_reader` with a custom `std::streambuf` that is fed by the content receiver,
  so the returned `std::istream` is genuinely incremental. A bounded internal buffer with
  producer/consumer coordination is needed because the receiver pushes while the consumer
  pulls; keep it simple (a small ring/queue with backpressure) and document the bound.
- `load(segments)` (the eager, returns-`vector` API) may remain for small/known callers,
  but should be implemented on top of the streaming path to avoid the double copy.
- **Range reads are preserved** — the existing per-segment `Range: bytes=offset-(offset+size-1)`
  logic (`s3_client.hpp:176-184`) is unchanged, including the `offset>0 || size>0` guard
  and 416-as-empty handling.

### Multipart writes (`save` / `save_from_reader`)

Implement the three-call multipart flow, reading the source `istream` in `partSize`
chunks:

1. **CreateMultipartUpload** — `POST /{bucket}/{key}?uploads`. Parse the `UploadId` from
   the XML response (a minimal substring scan for `<UploadId>…</UploadId>` — no XML
   library needed).
2. **UploadPart** (loop) — `PUT /{bucket}/{key}?partNumber=N&uploadId=UID` with the chunk
   as the (known-length, ≤ 32 MiB) body. Capture the `ETag` **response header** per part
   (no XML parse needed). Track the running byte count.
3. **CompleteMultipartUpload** — `POST /{bucket}/{key}?uploadId=UID` with a hand-built XML
   body listing `<Part><PartNumber>N</PartNumber><ETag>…</ETag></Part>`.
4. **AbortMultipartUpload** on any failure — `DELETE /{bucket}/{key}?uploadId=UID`.

- **Single-PUT fast path:** if the whole source fits in one part (≤ `partSize` and
  ≤ 32 MiB), skip multipart and use the existing single signed PUT.
- **Unknown size (`size < 0`):** read chunks until EOF; the number of parts is determined
  dynamically. The returned segment size is the **summed byte count** across parts
  (replacing today's `data.size()`).
- **`partSize`:** introduce a configurable part size (default 8 MiB; hard ceiling 32 MiB
  to respect the s3proxy non-chunked cap).

### SigV4 signing changes

The existing `sign_request(method, path, query, payload)` already accepts a `query`
argument but every caller passes `""`. Multipart requires real query strings, so:

- Thread the query string (`uploads`, `partNumber=N&uploadId=UID`, `uploadId=UID`) into
  `sign_request` and include it — URL-encoded and sorted — in the SigV4 **canonical
  request**. Verify the canonical-query encoding matches S3's rules (sorted by key,
  RFC-3986 encoded), since a mismatch surfaces only on the multipart calls.
- Each `UploadPart` signs over **its own** chunk payload hash (`sha256_hex(chunk)` via
  `crypto.hpp`). `CreateMultipartUpload` and `CompleteMultipartUpload` sign over their
  (empty / XML) bodies respectively.

### Orphan handling

- The client itself must `AbortMultipartUpload` on graceful mid-upload failure.
- Hard-crash orphans (`.mpus-*` staging) are swept by the operational mechanism described
  in the migration spec (decision **D11**): `list_multipart_uploads` + `abort`, or a
  periodic disk sweep of stale `.mpus-*`. That sweep is **not** built in this ticket but
  this client must not be the thing that leaks on ordinary errors.

## Implementation Tasks

1. **Part-size config + small refactor.** Add `partSize` to `S3Client` (default 8 MiB,
   ceiling 32 MiB); factor the existing single-PUT body-send into a helper reused by the
   fast path.
2. **SigV4 query support.** Wire the `query` argument through `sign_request` into the
   canonical request with correct encoding; add a unit test vector for a signed
   `?partNumber=1&uploadId=…` request.
3. **Multipart write.** Implement create/upload-loop/complete/abort in `save_from_reader`
   (and route `save(vector)` through it when the payload exceeds the part size); chunked
   reads from the `istream`; running byte count; minimal `<UploadId>` extraction;
   per-part `ETag` capture; hand-built completion XML.
4. **Streaming read.** Implement the content-receiver-backed `std::streambuf` and rebuild
   `load_reader` on it; reimplement `load` on top to drop the double copy; preserve Range
   + 416 handling.
5. **Comment cleanup.** Update stale "MinIO" wording to "S3" in `s3_client.hpp`
   (lines 18, 36, 44, 74) and `content.hpp` (file ABOUTME + the "MinIO/S3 content storage
   implementation" comment). (If the migration spec's Phase 6 already cleaned
   `s3_client.hpp`, just cover `content.hpp` here.)
6. **Tests** (below).

## Test Plan

Against an s3proxy container (matching production), plus unit tests for signing:

- **Round-trip, small:** save + load bytes/string unchanged (single-PUT fast path).
- **Multipart write:** save a `> partSize` payload (e.g. 20 MiB at 8 MiB parts → 3 parts);
  assert assembled size and content; assert a part-boundary Range read returns the
  correct bytes across the seam.
- **>32 MiB:** save 40 MiB; assert success via multipart (and that no single PUT > 32 MiB
  is attempted — would yield `MaxMessageLengthExceeded`).
- **Unknown size (`size < 0`):** save from a reader with unknown length; assert the
  returned segment size equals the bytes written.
- **Streaming read memory bound:** load a large object via `load_reader`; assert it can be
  consumed incrementally without materializing the whole object (e.g. read first N bytes
  and stop).
- **Abort on failure:** force a mid-upload error; assert `AbortMultipartUpload` is issued
  and `list_multipart_uploads` shows nothing left behind.
- **Edge cases:** 0-byte save/load; empty-object Range → 416-as-empty; offset reads at and
  across segment boundaries.
- **SigV4 unit vectors:** canonical request / signature for a query-bearing `UploadPart`.

## Risks / Footguns (C++-specific)

- **Per-part canonical-query signing.** Today's signer never exercises the query path; an
  encoding/sorting bug surfaces only at the multipart calls. Cover with an explicit
  signing test vector.
- **`std::streambuf` producer/consumer coordination.** The content receiver pushes on the
  HTTP thread while the consumer pulls; a naïve unbounded buffer reintroduces the memory
  problem, and a mis-bounded one can deadlock. Keep the buffer small and bounded with
  clear backpressure semantics; this is the trickiest piece.
- **Single `httplib::Client` serializes requests.** Fine for correctness (multipart is
  sequential here), but it precludes concurrent part uploads — note as a future
  throughput optimization, not a regression.
- **Part-size ceiling.** Must stay ≤ 32 MiB or large parts hit `MaxMessageLengthExceeded`;
  enforce the ceiling in code, not just docs.
- **No retries.** A transient failure aborts the whole upload (matches today's behavior).
  Acceptable for v1; flag a follow-up if C++ actions need resilience parity with the SDKs.

## Acceptance Criteria

- `load_reader` consumes a large object without fully buffering it in memory (verified by
  the streaming-read test).
- `save`/`save_from_reader` upload `> partSize` and `> 32 MiB` content successfully via
  multipart, with bounded memory and a correct returned byte count.
- Graceful mid-upload failure issues `AbortMultipartUpload`; no `.mpus-*` staging left
  behind (verified via `list_multipart_uploads`).
- Public `content.hpp` API signatures unchanged; existing C++ plugins recompile without
  source changes.
- No new third-party dependency added.
- Stale "MinIO" comments updated to "S3".
- `bin/changelog` entry created.

## Files to Touch

```
deltafi-cpp-action-kit/include/deltafi/internal/s3_client.hpp   # multipart write, streaming read, SigV4 query, partSize, comments
deltafi-cpp-action-kit/include/deltafi/content.hpp              # load_reader on streaming streambuf; comment cleanup
deltafi-cpp-action-kit/<tests>                                  # multipart/streaming/abort/signing tests
```
