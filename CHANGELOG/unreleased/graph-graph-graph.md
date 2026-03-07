# Changes on branch `graph-graph-graph`
Document any changes on this branch here.
### Added
- Added `deltafi graph` command for interactive time-series visualization from VictoriaMetrics
  - Supports 30+ predefined metrics via `--metric` flag (bytes, files, egress, CPU, memory, queue depth, etc.)
  - Supports custom MetricsQL queries via `--query` flag with optional `--title` to add to metric list
  - Interactive controls: zoom (+/-), refresh (r), series toggle (1-0), filter modal (f), change metric (c/↑/↓), help (?), and left/right panning
- Added `/api/v2/timeseries` REST endpoint in deltafi-core for proxying VictoriaMetrics queries

### Changed
- Moved `deltafi graph` (flow visualization) command to `deltafi flow graph`

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
-
