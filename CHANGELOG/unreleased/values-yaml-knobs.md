# Changes on branch `values-yaml-knobs`
Document any changes on this branch here.
### Added
- Enable/disable knobs in `site/values.yaml` for dashboards (Grafana), metrics (VictoriaMetrics), analytics, and log viewer (Dozzle)
- `deltafi.dashboards.enabled` — controls Grafana container and GrafanaAlertCheck in core
- `deltafi.metrics.enabled` — controls VictoriaMetrics container and `metricsEnabled` system property
- `deltafi.analytics.enabled` — controls analytics container and `timescaleAnalyticsEnabled`, `parquetAnalyticsEnabled`, `provenanceEnabled` system properties
- `deltafi.logviewer.enabled` — controls Dozzle container (Compose only)
- System properties locked by env var configuration show "Disabled by configuration" in the UI

### Changed
- Removed `depends_on: deltafi-victoriametrics` from nodemonitor and node-fastdelete (required for victoriametrics profile support)

### Fixed
-

### Removed
-

### Deprecated
- Helm `enable.grafana` — use `deltafi.dashboards.enabled` instead. When the new key is defined, `enable.grafana` is ignored.
- Helm `enable.victoriametrics` — use `deltafi.metrics.enabled` instead. When the new key is defined, `enable.victoriametrics` is ignored.

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
-
