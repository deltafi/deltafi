# Changes on branch `security-report`
Document any changes on this branch here.
### Added
- New Gradle build tasks:
  - `securityScan` — full sweep with per-tool gate
  - `grype` / `trivy` — per-image vulnerability scans
  - `scanSource` — repo-wide CVE scan of declared source dependencies
  - `scanConfig` — Dockerfile / Helm / k8s misconfig scan (Checkov, with `bridgecrew/checkov:latest` container fallback)
  - `scanSecrets` — repo-wide hardcoded-secret scan
  - `scanReferencedImages` — scans externally-pulled container images with trivy. Auto-populated from `tui/internal/orchestration/orchestrator.go` `*Container` defaults (excluding `Java*Container` dev/IDE images)
  - `sbom` / `collectSboms` — CycloneDX SBOMs per image and a fleet aggregator
  - `grypeReport` / `trivyReport` — combined per-image scan summaries
  - `trivyDbWarmup` — pre-warms the trivy DB so parallel scans don't collide
- Downstream DeltaFi plugins using `org.deltafi.plugin-convention` automatically inherit `grype`/`trivy`/`sbom`/`securityScan`.

### Changed
- `grypeReport` / `trivyReport` now auto-discover every docker-producing project, expanding image coverage from 5 to 11.
- Security-related tasks moved from the generic `verification` group to a dedicated `security` group.

### Fixed
- `scanSource` no longer crashes mid-walk on nested `build/` directories; default `skipDirs` now uses `**/` globs so subproject build artifacts and Go module caches are excluded everywhere they appear.
- Parallel trivy invocations no longer silently produce empty JSON. A `trivyDbAccess` Gradle build service serializes trivy execution; missing trivy results that previously appeared under load are gone.
- `grype`/`trivy` tasks no longer attempt to run a `null` executable when invoked from downstream plugins.
