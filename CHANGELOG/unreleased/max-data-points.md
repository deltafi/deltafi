# Changes on branch `max-data-points`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Fixed Grafana dashboard timeouts caused by excessive `maxDataPoints` values. With Graphite, high values meant "don't downsample." With VictoriaMetrics/PromQL, they cause the expression to be evaluated at every step (~100K times), overwhelming the server. Stat panels now use `maxDataPoints: 1`; time series panels use the Grafana default (panel width).

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- 
