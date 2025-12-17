# DeltaFi Project Instructions

## What is DeltaFi?

A data transformation, normalization, and enrichment platform. Data flows through the system via a publish-subscribe pattern: Data Sources ingest data as DeltaFiles, Transforms process them, and Data Sinks egress them. Every piece of data is tracked with full provenance.

## Documentation

Authoritative documentation lives in `deltafi-docs/docs/`. Consult when working on related areas:

| When working on... | Read... |
|-------------------|---------|
| Core concepts | `concepts.md`, `flows.md` |
| System architecture | `advanced/architecture.md` |
| Queue system (warm/cold) | `advanced/queue-architecture.md` |
| Action development | `actions.md`, `actions/*.md`, `action_parameters.md` |
| Plugin development | `plugins.md`, `getting-started/simple-plugin.md` |
| Lookup tables | `lookup-tables.md` |
| Error handling | `operating/errors.md`, `advanced/auto_resume.md` |
| UI changes | `operating/GUI.md` |
| CLI/TUI changes | `operating/TUI.md` |

This table is not exhaustive. Browse `deltafi-docs/docs/` for the full documentation.

## Module Structure

| Module | Purpose |
|--------|---------|
| `deltafi-core/` | Core platform - Spring Boot + DGS GraphQL. Manages DeltaFiles, dispatches actions, handles state |
| `deltafi-action-kit/` | Java SDK for building actions |
| `deltafi-python/` | Python SDK for building actions |
| `deltafi-common/` | Shared types between core and action-kit |
| `deltafi-common-test/` | Test utilities: in-memory storage mocks, test clock, test UUID generator |
| `deltafi-action-kit-test/` | AssertJ-style asserters for testing action results |
| `deltafi-core-actions/` | Built-in actions (transforms, egress, etc.) |
| `deltafi-ui/` | Vue.js frontend |
| `tui/` | Go CLI (the `deltafi` command) |
| `gradle-plugin/` | Gradle plugin for building DeltaFi plugins. Provides `org.deltafi.plugin-convention` |
| `orchestration/` | Docker Compose and Helm chart configs |
| `deltafi-docs/` | VitePress documentation site |

## Key Architectural Concepts

**DeltaFile lifecycle**: Created on ingress → queued for actions → actions execute → state updated → eventually egressed or errored. Understanding this state machine is critical for core changes.

**Storage separation**:
- PostgreSQL: DeltaFile metadata and state, system configuration
- MinIO (S3): Actual content/payload data
- Valkey: Action queues and message bus

**Two-tier action queues**: Actions are dispatched via a warm queue (Valkey, in-memory, fast) and cold queue (PostgreSQL, on-disk, overflow). When the warm queue for an action class reaches `inMemoryQueueSize` (default 5000), new items overflow to cold queue. Items automatically warm up when space is available. See `advanced/queue-architecture.md` for details.

**Publish-subscribe pattern**: Data Sources and Transforms publish to topics. Transforms and Data Sinks subscribe to topics. Routing can be conditional via SpEL expressions.

**Actions are stateless**: They receive a message, do work, return a response. The Core manages all state.

## Build & Test

```bash
# Build and deploy everything
./gradlew install

# Build specific module (faster iteration)
./gradlew :deltafi-core:install
./gradlew :deltafi-action-kit:install

# Run tests
./gradlew test
./gradlew :deltafi-core:test
./gradlew :deltafi-core:test --tests "SomeTestClass"

# Build TUI
./gradlew tui

# Publish gradle-plugin to local Maven (for testing plugin changes in external projects)
./gradlew :gradle-plugin:publishToMavenLocal

# UI development (UI is bundled with deltafi-core, not a separate gradle module)
cd deltafi-ui && ./runDev.sh      # UI at localhost:8080 pointing to remote dev server (UI-only changes)
cd deltafi-ui && ./runLocal.sh    # UI at localhost:8080 pointing to local core (for testing with local backend)
cd deltafi-ui && npm run lint
cd deltafi-ui && ./runTest.sh     # Run Cypress e2e tests (starts mock server, runs tests, cleans up)
./gradlew :deltafi-core:install   # Builds UI and deploys to docker
```

## Git Workflow

The `dev` branch is the main development branch. Always branch off `dev`:
```bash
git checkout dev
git pull
git checkout -b feature/my-feature
```

When submitting changes, create a changelog entry:
```bash
bin/changelog
```
This creates `CHANGELOG/unreleased/<branch-name>.md`. Keep all section headers in the generated file even if empty (Added, Changed, Fixed, etc.). Note: Do NOT use `-e` flag as it opens an interactive editor.

## API

The core exposes both GraphQL (via Netflix DGS) and REST endpoints. Schema files are in `deltafi-core/src/main/resources/schema/`.

## Testing Notes

- Core tests use Spring Boot test slices
- Action kit has its own test framework (`deltafi-action-kit-test`)
- UI tests use Cypress e2e tests with MSW (Mock Service Worker) for API mocking

## Improving the Project

When you notice gaps, suggest fixes:
- Missing or outdated documentation in `deltafi-docs/` → suggest additions
- Missing test coverage → suggest tests
- Gaps in this file → suggest updates to `CLAUDE.md`
