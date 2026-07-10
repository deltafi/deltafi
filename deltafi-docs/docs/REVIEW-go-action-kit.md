<!--
ABOUTME: Gap report for Go action kit coverage in deltafi-docs.
ABOUTME: Not linked into site nav — reference document for Jeremy.
-->

# Go Action Kit Documentation Review

*Branch: go-for-it — reviewed against the final shipped API.*

---

## Inventory

| Page | Mentions Go? | Java-only sections? | Accuracy vs current Go API | Notes |
|------|-------------|---------------------|---------------------------|-------|
| `docs/actions.md` | Yes | Yes (Context, Input, SSL) | **Stale** — `SaveStringContent(data, name, mt)` is wrong order | Go sections present but content-save arg order was payload-first (old) |
| `docs/actions/transform.md` | Yes | No | **Stale** — API list and example both show old payload-first `Save*` order | Streaming `Save*` variants and batch APIs absent |
| `docs/actions/egress.md` | Yes | No | Accurate | No issues found |
| `docs/actions/timed_ingress.md` | Yes | No | **Stale** — `SaveBytesContent` arg order wrong in example | Streaming `Save*` for `IngressDeltafile` absent |
| `docs/action_parameters.md` | Yes | No | Accurate | Go `EnvVar` and nested params correct |
| `docs/plugins.md` | Yes | No | Accurate | Go plugin structure, Makefile, schema round-trip all correct |
| `docs/unit-test.md` | Yes | No | **Stale** — `NewIngressInput()` does not exist; `NewTimedIngressDeltafile()` is correct | Race-detector `acceptance-test` target absent; `RunJoin` absent |
| `docs/lookup-tables.md` | Yes | No | Mostly accurate | `lookupResults.Rows` is now `[]Row` not `[]map[string]string`; `Row.Get()` not shown; embedded suppliers absent |
| `docs/artifact-registry.md` | Yes | No | Accurate | `ArtifactAware` pattern, `WatchArtifact`, `SafeStringMap` all present |
| `docs/advanced/multithreading_java_action_kit.md` | Yes | No | Accurate for thread counts | **Missing**: shared-singleton contract, data-race hazards, `acceptance-test -race` target |
| `docs/getting-started/for-plugin-developers.md` | Yes | No | **Stale** — `SaveStringContent` arg order wrong in example | Go Interface column in action types table is a misnomer; Go has no named action interfaces |

---

## Gap Report

### Blocking

None found that would cause a build failure or silent data corruption at runtime. (The wrong arg order would cause a compile error — the call would still compile because all three string args are `string`, but the **semantics** are wrong: content would be saved under the name equal to the data string.)

### Important

**I-1: `Save*` argument order wrong in five places (payload was first; now name-first/payload-last)**

The shipped API (`Task 1`) reordered all `Save*` methods so data/reader/writer comes last:
```go
// Correct (current)
SaveStringContent(name, mediaType, data string)
SaveBytesContent(name, mediaType string, data []byte)
SaveReaderContent(name, mediaType string, r io.Reader)
SaveWriterContent(name, mediaType string, fn func(io.Writer) error)
```
Affected pages (all now fixed — see Fixes Applied):
- `docs/actions.md` line 255
- `docs/actions/transform.md` API list (lines 158–159) and example (line 262)
- `docs/actions/timed_ingress.md` example (line 191)
- `docs/getting-started/for-plugin-developers.md` example (line 246)

**I-2: `TestContext.NewIngressInput()` does not exist**

`docs/unit-test.md` (lines 122 and 215) refers to `tc.NewIngressInput()` which returns `*IngressInput`. The actual method is `tc.NewTimedIngressDeltafile()` returning `*TimedIngressDeltafile`. This would fail to compile in any test that follows the doc. Now fixed.

**I-3: No concurrency / shared-singleton contract documentation for Go**

`docs/advanced/multithreading_java_action_kit.md` covers Java thread counts and Go/C++ env-var configuration — but says nothing about the Go shared-singleton contract (the same action struct instance is used by all worker goroutines concurrently), which fields are safe to read vs. write, `SafeStringMap` as the kit-provided concurrency primitive, or the `make acceptance-test` / `go test -race` workflow. Java developers have `volatile` and `synchronized` guidance in the Java kit; Go developers need equivalent guidance. This is the most significant undocumented behavioral contract in the kit. Proposed follow-up: add a Go section to `multithreading_java_action_kit.md` covering the shared-singleton model, stateless-action idiom, `SafeStringMap`, and the `acceptance-test` target.

**I-4: Streaming `Save*` variants and batch APIs absent from all pages**

`SaveWriterContent`, `SaveReaderContent`, `SaveContentBatch`, `SaveWriterContentBatch` (shipped in Task 2/4) are not mentioned in any doc page other than in passing references. The streaming best-practices content in `DESIGN.md` has no equivalent in `deltafi-docs`. Plugin developers who follow the docs will default to `SaveStringContent`/`SaveBytesContent`, buffering potentially large content in memory. Proposed follow-up: add a "Streaming Content" callout to `docs/actions/transform.md` and `docs/actions/timed_ingress.md`.

### Minor

**M-1: `LookupResults.Rows` is `[]Row`, not `[]map[string]string`**

`docs/lookup-tables.md` references `lookupResults.Rows` in a comment without showing how to access individual values. The `Row` type (shipped in Task 5) exposes `.Get(col)` and `.Has(col)` — there is no direct map-index access (`row["col"]`). The DESIGN.md still documents `Rows` as `[]map[string]string`. Neither is catastrophic (the comment is vague), but any developer who tries `lookupResults.Rows[i]["col"]` will get a compile error. The mock setup API (`MockLookupClient.Tables[].Rows []LookupRow`) still uses `map[string]string` correctly for the *setup* side. Proposed follow-up: add a small example showing `row, _ := results.Rows[0].Get("col")` after the existing lookup snippet.

**M-2: `NewEmbeddedCSVSupplier` / `NewEmbeddedJSONSupplier` absent**

These constructors (Task 5) let plugin authors bundle static lookup-table data inside the binary without writing a full supplier struct. Not mentioned anywhere in `docs/lookup-tables.md`. Proposed follow-up: add a "Embedded Suppliers" subsection under the Go supplier section.

**M-3: `Fail` / `Failf` absent**

`df.Fail(err)` and `df.Failf(format, args...)` (Task 3, convenience wrappers over `Errorf`) are not mentioned in any doc. `Filterf` and `Errorf` are documented; the `Fail`/`Failf` variants are not. Proposed follow-up: add a line to the error/filter signaling list in `docs/actions/transform.md`.

**M-4: Result precedence (children beat error/filter) absent**

The rule that `AddChild` takes precedence over `Errorf` (DESIGN.md: Result Precedence) is an important footgun. Not documented in deltafi-docs. Proposed follow-up: add a "Result Precedence" note to `docs/actions/transform.md` under the Go section.

**M-5: `RunJoin` absent from `docs/unit-test.md`**

`actionkit.RunJoin` is listed in DESIGN.md but not in `docs/unit-test.md`'s Go Action Testing section. Proposed follow-up: add a "Testing Join Actions" subsection alongside the existing "Testing Split Actions" subsection.

**M-6: Go action type table column header in `for-plugin-developers.md` is misleading**

The action types table (line 152) has a "Go Interface" column with values like `TransformAction`, `TimedIngressAction`, `EgressAction`. Go has no such interfaces; actions are plain structs with conventionally-named methods registered via `RegisterTransform`/`RegisterEgress`/`RegisterIngress`. The surrounding example code is correct. Proposed follow-up: rename the column to "Go Registration" and update entries to `RegisterTransform`, `RegisterEgress`, `RegisterIngress`.

**M-7: `acceptance-test` Makefile target absent from plugin developer docs**

The `make acceptance-test` target (which runs `go test -race ./...`) is defined in the kit's Makefile and recommended for Go plugins, but is not mentioned in `docs/plugins.md`'s Makefile section. The template Makefile shown has only `build`, `test`, `vet`. Proposed follow-up: add `acceptance-test` to the template Makefile and a note about it.

**M-8: DESIGN.md inside the action kit has stale arg order examples**

This is outside the scope of deltafi-docs but worth noting: `DESIGN.md` lines ~374–379 and ~432–435 still show the old payload-first `SaveStringContent`/`SaveBytesContent` call order, and its lookup-table section still describes `Rows` as `[]map[string]string`. These should be updated in a follow-up pass on the kit itself.

---

## Fixes Applied

All fixes are minimal, matched to surrounding style.

### Fix 1 — `docs/actions.md` line 255
**Before:** `df.SaveStringContent(data, fileName, "text/plain")`  
**After:** `df.SaveStringContent(fileName, "text/plain", data)`

### Fix 2 — `docs/actions/transform.md` API list (lines 158–159)
**Before:**
```go
df.SaveStringContent(data, name, mediaType)   // *ActionContent, error
df.SaveBytesContent(data, name, mediaType)    // *ActionContent, error
```
**After:**
```go
df.SaveStringContent(name, mediaType, data)   // *ActionContent, error
df.SaveBytesContent(name, mediaType, data)    // *ActionContent, error
```

### Fix 3 — `docs/actions/transform.md` example (line 262)
**Before:** `df.SaveStringContent(transformed, "transform-named-me", "text/plain")`  
**After:** `df.SaveStringContent("transform-named-me", "text/plain", transformed)`

### Fix 4 — `docs/actions/timed_ingress.md` example (line 191)
**Before:** `item.SaveBytesContent([]byte(contentData), filename, "text/plain")`  
**After:** `item.SaveBytesContent(filename, "text/plain", []byte(contentData))`

### Fix 5 — `docs/unit-test.md` API overview (line 122)
**Before:** `TestContext.NewIngressInput()` — creates an `*IngressInput` for ingress action tests  
**After:** `TestContext.NewTimedIngressDeltafile()` — creates a `*TimedIngressDeltafile` for ingress action tests

### Fix 6 — `docs/unit-test.md` ingress test example (lines 215–217)
**Before:**
```go
input := tc.NewIngressInput()
event := actionkit.RunIngress(t, &MyIngressAction{}, input, MyIngressParams{})
```
**After:**
```go
timed := tc.NewTimedIngressDeltafile()
event := actionkit.RunIngress(t, &MyIngressAction{}, timed, MyIngressParams{})
```

### Fix 7 — `docs/getting-started/for-plugin-developers.md` example (line 246)
**Before:** `df.SaveStringContent(transformed, "output.txt", "text/plain")`  
**After:** `df.SaveStringContent("output.txt", "text/plain", transformed)`

---

## Proposed Follow-Ups for Jeremy

In priority order:

1. **(Important)** Add a "Go Concurrency" section to `docs/advanced/multithreading_java_action_kit.md` covering: shared-singleton model, stateless-action idiom, `SafeStringMap`, `sync.RWMutex` pattern, and `make acceptance-test` / `go test -race` workflow.

2. **(Important)** Add streaming content callouts to `docs/actions/transform.md` and `docs/actions/timed_ingress.md` covering `SaveReaderContent`, `SaveWriterContent`, `SaveContentBatch`, `SaveWriterContentBatch`.

3. **(Minor)** Add `Row.Get()` example to the Go lookup section in `docs/lookup-tables.md`.

4. **(Minor)** Add "Embedded Suppliers" subsection to `docs/lookup-tables.md` with `NewEmbeddedCSVSupplier` / `NewEmbeddedJSONSupplier`.

5. **(Minor)** Add `Fail`/`Failf` to the error signaling list in `docs/actions/transform.md`.

6. **(Minor)** Add result-precedence note (children beat error/filter) to `docs/actions/transform.md`.

7. **(Minor)** Add `RunJoin` / "Testing Join Actions" to `docs/unit-test.md`.

8. **(Minor)** Fix the Go column header in the action-types table in `docs/getting-started/for-plugin-developers.md`.

9. **(Minor)** Add `make acceptance-test` to the template Makefile in `docs/plugins.md`.

10. **(Action kit internal)** Update `DESIGN.md` examples to use the current payload-last arg order and update the lookup section's `Rows` type annotation from `[]map[string]string` to `[]Row`.
