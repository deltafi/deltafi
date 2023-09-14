# Changes on branch `clickhouse-ttl`
Document any changes on this branch here.
### Added
- Configurable time-to-live duration for ETL Deltafiles table

### Changed
- Clickhouse ETL table renamed `deltafiles`
- Clickhouse Grafana charts are not loaded if clickhouse is disabled
- Clickhouse ETL table is partitioned to days to improve short range query performance

### Fixed
- Clickhouse flow-subflow chart had misnamed bytes graph

### Removed
- 

### Deprecated
- Clickhouse ETL table `deltafile` is no longer used

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- Prototype `deltafile` table should be delete from clickhouse if clickhouse was enabled prior to this release
