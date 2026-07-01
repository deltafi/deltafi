# Security Scanning

DeltaFi runs vulnerability and misconfiguration scans against built images, source-tree dependencies, infrastructure-as-code, and the source tree itself. All scans are exposed as Gradle tasks under the `Security tasks` group and aggregated by a single root task — `securityScan` — that applies per-tool severity gates and emits a markdown + JSON summary report.

## Running scans

The `Security tasks` group is visible from `./gradlew tasks`:

| Task | Scope | Tool |
|------|-------|------|
| `:<project>:grype` | One project's Docker image | grype |
| `:<project>:trivy` | One project's Docker image | trivy |
| `:grypeReport` | All built images, combined summary | grype |
| `:trivyReport` | All built images, combined summary | trivy |
| `:grypeFullReport` | Every installed DeltaFi image (after `install`) | grype |
| `:trivyFullReport` | Every installed DeltaFi image (after `install`) | trivy |
| `:scanSource` | Repo source tree (declared dependencies) | trivy fs |
| `:scanConfig` | Dockerfiles, Helm charts, k8s manifests | Checkov |
| `:scanSecrets` | Repo source tree (hardcoded secrets) | trivy |
| `:scanReferencedImages` | Externally-referenced container images (e.g. `deltafi/*` from the TUI orchestrator) | trivy |
| `:<project>:sbom` | One project's Docker image | syft |
| `:collectSboms` | Every docker-producing project | syft |
| `:securityScan` | Aggregates everything, applies the gate | — |

Common entry points:

```bash
./gradlew securityScan                # full sweep + gate (fails the build on findings)
./gradlew securityScan -PfailOn=NONE  # full sweep, never fail (report-only mode)
./gradlew :deltafi-core:grype         # quick grype scan of one image
./gradlew scanSecrets                 # repo-wide secret scan
```

The aggregated summary is written to `build/reports/security/summary.md` (human-friendly) and `summary.json` (machine-readable).

## Configuring the gate

The `securityScan` extension at the project root controls the per-tool severity threshold, the `ignoreUnfixed` policy, and where suppressions/reports live. Defaults are wired into the `security-scan` precompiled script plugin; override in the root `build.gradle` only when needed:

```groovy
securityScan {
    failOn {
        trivyImage  = 'HIGH'      // image CVEs
        grypeImage  = 'CRITICAL'  // grype is noisier; tighter threshold
        trivyFs     = 'HIGH'      // source-dep CVEs
        checkov     = 'HIGH'      // IaC/Dockerfile/Helm/k8s misconfig
        trivySecret = 'LOW'       // any secret fails
    }
    ignoreUnfixed = true          // exclude CVEs without an upstream fix from the gate (still reported)
    sarif = false                 // when true, every scan also writes a SARIF file alongside the JSON (~doubles scan time)
    suppressionsDir = file('.security')
    reportDir = layout.buildDirectory.dir('reports/security').get().asFile
    sbomDir = layout.buildDirectory.dir('sboms').get().asFile
    // Auto-populated from tui/internal/orchestration/orchestrator.go when present.
    // Override or extend to scan additional externally-pulled images.
    referencedImages = [
        'deltafi/grafana:12.4.5-0',
        'deltafi/timescaledb:2.19.3-pg16-10',
        // ...
    ]
}
```

The `-PfailOn=<SEVERITY|NONE>` CLI override applies to **every** tool simultaneously for one-off runs (CI dry runs, local triage). It bypasses the per-tool config without editing the build.

### Gate semantics

- `failOn = 'HIGH'` fails the build on `HIGH` **and** `CRITICAL` findings. Lowest-severity-that-fails, not exact-match.
- `failOn = 'NONE'` never fails — the report is still produced.
- Findings filtered out by `.security/` suppression files are excluded **before** the gate evaluates.
- `ignoreUnfixed = true` excludes vulnerabilities with no available fix from gate evaluation — they still appear in the summary so they're visible. Config and secret findings always participate (there is no "fix version" concept).

## Suppressing findings (`.security/`)

Place suppression files in the `.security/` directory at the repo root. Each tool reads its native format; the build wires the right `--config`/`--ignorefile` flag for you.

```
.security/
├── trivyignore     # trivy: one CVE/secret ID per line, '#' for comments
├── grype.yaml      # grype: ignore list, see grype --help for full syntax
└── checkov.yaml    # checkov: --config-file with skip-check / skip-path / etc.
```

Examples:

`.security/trivyignore`:
```
# Pinned for $REASON, tracked in $TICKET
CVE-2024-12345
```

`.security/grype.yaml`:
```yaml
ignore:
  - vulnerability: CVE-2024-12345
  - vulnerability: CVE-2024-67890
    package:
      name: some-package
```

A suppression entry should always have an inline justification (ticket, expiration date, or reason) so the next reviewer knows whether it's still warranted.

## Downstream plugins

External DeltaFi plugins using the published `org.deltafi.plugin-convention` automatically inherit:

- `org.deltafi.docker-scan` — `grype` and `trivy` tasks against the plugin's image
- `org.deltafi.security-scan` — `securityScan` aggregator + gate + summary report

Plugin authors configure `securityScan { failOn { ... } }` in their own `build.gradle` and drop suppression files into their own `.security/` directory. The source/config/secret scans (`scanSource`, `scanConfig`, `scanSecrets`) are monorepo-only.

## Output files

The aggregator writes:

- `build/reports/security/summary.md` — Markdown report with the gate verdict and a per-scan finding table
- `build/reports/security/summary.json` — Same data as JSON for CI ingestion

Each per-scan task also writes its raw output under `build/`:

- `build/grype-results/<project>-grype-results.json`
- `build/trivy-results/<project>-trivy-results.json`
- `build/scan-results/source/trivy-fs.json`
- `build/scan-results/config/checkov.json`
- `build/scan-results/secrets/trivy-secrets.json`
- `build/trivy-results/referenced-<image>-trivy-results.json` — one per externally-referenced image scanned by `scanReferencedImages`
- `build/sboms/<project>.cdx.json` — CycloneDX SBOM (from `sbom`/`collectSboms`)

When `securityScan { sarif = true }` is set, each scan also writes a `.sarif` file alongside its `.json` output in the same directory. SARIF is consumable by GitHub Code Scanning, IDE plugins (e.g. SARIF Viewer for IntelliJ/VS Code), and most security CI dashboards.

## Tool fallback

If a scan tool isn't on `PATH` locally, tasks pull a Docker image and run the tool from there transparently — no manual install required. Defaults are the upstream tags:

| Tool | Native binary | Default container fallback |
|------|---------------|----------------------------|
| grype | `grype` | `anchore/grype:latest` |
| trivy | `trivy` | `aquasec/trivy:latest` |
| syft  | `syft`  | `anchore/syft:latest` |
| checkov | `checkov` | `bridgecrew/checkov:latest` |

Override any entry to pin to a specific version or point at a mirrored image (e.g. for offline / air-gapped builds, or to lock CI on a specific scanner version):

```groovy
securityScan {
    toolImages.grype = 'deltafi/grype:0.71.0-1'
    toolImages.trivy = 'deltafi/trivy:0.71.0-1'
}
```

Plugin authors override per-project in their own `build.gradle`. Installing the native binary is faster (no per-run docker pull) but otherwise equivalent.

## Adding a new image to coverage

`securityScan`, `grypeReport`, and `trivyReport` auto-discover any subproject that applies one of the docker convention plugins (`docker-java` or `docker-plain`). Apply the right plugin and the project's image will be included in the next scan run.

## Troubleshooting

- **`null` executable error**: Update to the current `gradle-plugin` version — older builds shipped a bug where `ToolRunner.getExecutable()` resolved against the Gradle `ExecSpec` delegate. Fixed by capturing the value into a local before the exec closure.
- **Trivy "context canceled" / "FATAL" during `scanSource`**: A subdirectory got into trouble during analysis. Add it to `securityScan { skipDirs += 'subdir-name' }`. The default skip list already includes `build`, `.gradle`, `node_modules`, `m2`, `.git`, `dist`, `out`, `.vitepress`.
- **`scanConfig` reports many findings**: Checkov pre-renders Helm charts before scanning, which keeps the misconfig count much lower than per-template scanners. Triage recurring rule IDs via `.security/checkov.yaml` (`skip-check`) rather than narrowing `configPaths`. If `checkov` is not on PATH the task falls back to the `bridgecrew/checkov:latest` image automatically.
