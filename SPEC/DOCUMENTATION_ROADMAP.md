# Documentation Improvement Roadmap

This document tracks planned documentation improvements for DeltaFi.

## Status

- **Phase 1**: ✅ Complete - Entry points and persona guides
- **Phase 2**: 🔲 Planned - Fill empty documentation
- **Phase 3**: 🔲 Planned - TUI documentation expansion
- **Phase 4**: 🔲 Planned - Architecture and accuracy audit
- **Phase 5**: 🔲 Planned - Advanced topics

---

## Phase 1: Critical Entry Points (Complete)

**Goal**: Fix the newcomer experience by providing clear paths for different user personas.

**Deliverables**:
- [x] Rewrite `CONTRIBUTING.md` (minimal, points to deltafi-docs)
- [x] Expand `README.md` (repo structure, persona links)
- [x] Create `for-operators.md` in deltafi-docs
- [x] Create `for-plugin-developers.md` in deltafi-docs (includes plugin scenarios: new vs existing)
- [x] Create `for-core-developers.md` in deltafi-docs
- [x] Update VitePress sidebar
- [x] Document site directory customization (values.yaml, compose.yaml, kind.values.yaml, templates/)
- [x] Add "Understanding the TUI" section to TUI.md (concepts, version model, orchestration modes)
- [x] Link persona guides to TUI conceptual docs
- [x] Write `operating/configuration.md` (TUI config, site config files)
- [x] Fix outdated "coming shortly" claim in `operating/errors.md`
- [x] Delete obsolete install guides (`install/compose.md`, `install/kind.md`, `kind.md`)
- [x] Update `operating/metrics.md` - replace Graphite references with VictoriaMetrics

---

## Phase 2: Fill Empty Documentation

**Goal**: Write content for stub/empty documentation files.

**Deliverables**:
- [ ] `deltafi-docs/docs/operating/GUI.md` - Web interface documentation

**Notes**:
- GUI.md requires research into deltafi-ui capabilities

---

## Phase 3: TUI Documentation Expansion

**Goal**: Document undocumented and under-documented TUI features. (Basic TUI concepts were added in Phase 1; this phase covers deeper details.)

### 3.1 Completely Undocumented Commands

| Command | Description | Priority |
|---------|-------------|----------|
| `properties view` | Interactive property browser/editor | High |
| `postgres migrations` | Flyway migration tracking | Medium |
| `lookup cli` | Lookup database CLI access | Medium |
| `docs` | In-terminal documentation browser | Low |

### 3.2 Under-documented Commands

| Command | Gap | Priority |
|---------|-----|----------|
| `topic upstream/downstream/graph` | Flow analysis tools barely mentioned | High |
| `freeze` | What is a "frozen distribution"? | High |
| `delete-policies` | No configuration format examples | Medium |
| `plugin generate` | Not all action types listed | Medium |
| `upgrade --safe` | Best practices not documented | Medium |
| `integration-test` | YAML format needs examples | Low |

---

## Phase 4: Architecture and Accuracy Audit

**Goal**: Improve architecture documentation and fix outdated content.

### 4.1 Architecture Expansion

**File**: `deltafi-docs/docs/advanced/architecture.md`

Current state: ~40 lines, very minimal.

**Needed content**:
- [ ] Component diagram
- [ ] Data flow through the system
- [ ] Service interactions
- [ ] Content storage model (segments)
- [ ] Join operations explanation

---

## Phase 5: Advanced Topics

**Goal**: Document advanced features that exist but are underdocumented.

### From deltafi-core

| Feature | Location | Gap |
|---------|----------|-----|
| Join Operations | ScheduledJoinService, JoinEntryService | Mechanics, criteria, timing unexplained |
| State Machine | StateMachine service | Legal state transitions undocumented |
| System Snapshots | Snapshotter, SystemSnapshotService | Recovery procedures not documented |
| Content Segmentation | Segment-based storage | Streaming architecture unexplained |
| Content Caching | DeltaFileCacheService | Cache strategies undocumented |

### From deltafi-action-kit

| Feature | Location | Gap |
|---------|----------|-----|
| Split Operations | TransformResults, ChildTransformResult | One-to-many transforms |
| Exception-based Errors | ErrorResultException, FilterResultException | Throwing vs returning |
| Content Tags | addTag(), hasTag(), removeTag() | Tag system on ActionContent |
| Subcontent | subcontent(offset, size, name) | Partial content references |
| ActionOptions | inputSpec, outputSpec | Self-documenting action framework |

### Missing Sections

- [ ] Glossary/terminology reference
- [ ] Troubleshooting/FAQ
- [ ] Performance tuning guide
- [ ] Security hardening guide
- [ ] API reference (REST/GraphQL endpoints)

