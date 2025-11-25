# Changes on branch `victoria-metrics`
Document any changes on this branch here.
### Added
-

### Changed
- Replace Graphite with VictoriaMetrics for metrics storage

### Fixed
-

### Removed
- 

### Deprecated
- 

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
- VictoriaMetrics v1.129.1
- New persistent volume claim is required for deltafi-victoriametrics in k8s
- Migration from Graphite to VictoriaMetrics requires running `bin/migrate-graphite-to-victoriametrics.sh` to preserve historical metrics. This should be run after installation of the new version. Once run the old graphite data may be deleted.
- A new version of the deltafi TUI is required to install this in a dev environment
- Grafana dashboards using the summarize function with a fixed time interval will need to be corrected. This pattern was present in several of the default dashboards, so dashboards clones and modified from these will no longer work until this change is made.
```diff
- summarize(seriesByTag('name=stats_counts.bytes_from_source', 'dataSource=~${dfDataSource:regex}'), '1d', 'sum', false)
+ summarize(seriesByTag('name=stats_counts.bytes_from_source', 'dataSource=~${dfDataSource:regex}'), '$__interval', 'sum', false)
```
