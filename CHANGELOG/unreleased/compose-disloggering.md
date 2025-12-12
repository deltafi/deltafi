# Changes on branch `compose-disloggering`
Document any changes on this branch here.
### Added
- Dozzle log viewer added to main administration sidebar
- Vector log aggregation to the local file system
- Logrotate added to monitor log files with configurable size-based and time-based rotation
- Log stack can be disabled in the compose stack (`deltafi.logs.enable: false`)
- Comprehensive logging documentation at `/operating/logging`
- Site values template updated with logging configuration defaults

### Changed
- Grafana dashboards reorganized into `core/` and `logs/` subdirectories for better organization
- Grafana datasources configuration updated to remove Loki
- Nginx configuration updated with auth cache settings

### Fixed
-

### Removed
- Loki removed from compose orchestration
- Promtail removed from compose orchestration
- Grafana log dashboards removed from compose orchestration (log viewing now uses Dozzle)
- Graphite configuration files removed from compose orchestration

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- Compose: Adding new container deltafi/logrotate:1.0.0-1
- Compose: Adding new container deltafi/vector:0.51.1-alpine-0
