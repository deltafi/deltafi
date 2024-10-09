# Changes on branch `grindhouse`
Document any changes on this branch here.
### Added
- TimescaleDB added to all orchestration systems
- Clickhouse analytic event tables replaced with TimescaleDB/PostgreSQL analytic event tables
- Grafana: PostgreSQL data source added
- Grafana: New dashboards created for annotation and error analytics

### Changed
- KinD: `cluster manifest` changed to `cluster images` to line up with docker and compose commands

### Fixed
- KinD: Missing `_is_arm()` function restored

### Removed
- Clickhouse removed for all orchestration systems
- Grafana: Clickhouse diagnostic dashboards removed
- Grafana: Clickhouse analytics removed

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- 
