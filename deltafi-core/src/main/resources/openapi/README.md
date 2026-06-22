# DeltaFi REST API — OpenAPI specification

`openapi.yaml` in this directory is the **generated, authoritative** OpenAPI 3.1
description of the DeltaFi REST API (the `/api/v2/**`, `/plugins`, etc. endpoints
served by `deltafi-core`). It plays the same role for REST that the
`src/main/resources/schema/*.graphqls` files play for GraphQL: a single committed
contract that downstream Go (TUI) and TypeScript (UI) clients are generated from.

## Do not edit by hand

The file is produced from the live Spring controllers by springdoc. To change it,
change the controllers / DTOs and regenerate:

```bash
./gradlew :deltafi-core:generateOpenApiSpec
```

This task boots the application context at `OpenApiSpecGeneratorTest` with mocked
persistence and infrastructure (no database or Docker required), captures
`/v3/api-docs.yaml`, canonicalizes it (all map keys sorted) for deterministic output,
and overwrites `openapi.yaml`.
The generator is tagged `openapi` and is excluded from the normal `test` task so
routine test runs do not rewrite this file.

## How it stays correct

CI job **"OpenAPI Spec Drift"** (`.gitlab-ci.yml`) enforces two gates on every
pipeline:

1. **Drift** — regenerates the spec and fails if it differs from what is committed.
   So any REST API change must be regenerated and committed, landing as a reviewable
   diff here.
2. **Breaking change** — `oasdiff` compares this branch's spec against `origin/dev`
   and fails the pipeline on a backward-incompatible change.

## Stable metadata

Title, version, and a single relative server URL (`/`) are pinned in
`org.deltafi.core.configuration.OpenApiConfig` so the spec only changes when the
API changes — not on every regeneration.
