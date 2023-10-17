# Changes on branch `pod-metrics`
Document any changes on this branch here.
### Added
- New clustermonitor pod that polls kubernetes app metrics and records per-app CPU (in milli-cores) and RAM utilization
- New metrics produced:
  - `gauge.app.memory` tagged by pod container name
  - `gauge.app.cpu` tagged by pod container name
- `System Overview` dashboard updated with the following graphs:
  - `Pod RAM Utilization Over Time`
  - `Pod RAM Utilization` pie chart
  - `Pod CPU Utilization Over Time`
  - `Pod CPU Utilization` pie chart

### Changed
- 

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Refactored nodemonitor configuration in values.yaml to simplify and remove exposure of environment variables.

### Upgrade and Migration
- 
