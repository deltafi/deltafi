# Changes on branch `security-report`
Document any changes on this branch here.
### Added
- Gradle security scanning organized into three layers:
  - **Scan** — `:project:grype` and `:project:trivy` stream tool output for ad-hoc human use (no artifacts).
  - **Report** (artifacts, never gate on findings):
    - Per-subproject `:project:grypeReport` and `:project:trivyReport` emit `<project>/build/reports/security/{grype,trivy}.{json,md}`.
    - Root `:grypeReport` and `:trivyReport` aggregate every per-subproject result, scan the configured `securityScan.referencedImages`, and emit `build/reports/security/{grype,trivy}.{json,md}`.
    - `:securitySourceReport` (trivy fs), `:securityConfigReport` (Checkov), `:securitySecretsReport` (trivy secrets) emit `build/reports/security/{source,config,secrets}.{json,md}`.
    - `:securityReport` stitches all five tool reports into `build/reports/security/security.md` with a per-tool at-a-glance table.
  - **Gate** (read existing JSON, evaluate threshold):
    - Per-tool: `:grypeGate`, `:trivyGate`, `:securitySourceGate`, `:securityConfigGate`, `:securitySecretsGate`.
    - Aggregate: `:securityGate` depends on all five sub-gates.
    - Thresholds come from `securityScan.failOn.{grypeImage, trivyImage, trivyFs, checkov, trivySecret}`; `-PfailOn=<severity>` overrides for the invocation.
- DB warmup tasks: `:grypeDbWarmup` and `:trivyDbWarmup` run once per build. All in-task tool invocations skip the in-tool DB update (`grype --no-db-auto-update`, `trivy --skip-db-update --skip-java-db-update`).
- `:sbom` per image + `:collectSboms` aggregator (CycloneDX, syft).
- `trivyDbAccess` build service (`maxParallelUsages=1`) serializes every trivy-bearing task across the build to prevent boltdb cache contention that otherwise produces silently-empty JSON output.
- `securityScan` DSL extension: `failOn` per tool, `suppressionsDir`, `referencedImages`, `toolImages`, `sarif`, `ignoreUnfixed`, `sourcePath`, `secretsPath`, `configPaths`, `skipDirs`, `configSkipPaths`, `sbomDir`, `reportDir`.
- `referencedImages` is auto-populated from `tui/internal/orchestration/orchestrator.go` `*Container` defaults (excluding `Java*Container` dev/IDE images).
- Downstream DeltaFi plugins using `org.deltafi.plugin-convention` automatically inherit the full scan/report/gate task set.
