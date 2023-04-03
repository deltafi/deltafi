# Changes on branch `462-infrastructure-agnostic-system-metrics`
Document any changes on this branch here.
### Added
-

### Changed
- The nodemonitor to reports CPU and memory metrics to Graphite.
- Changes to the API endpoint for nodes metrics (`/api/v1/metrics/system/nodes`):
  - Node metrics are now pulled from Graphite instead of Kubernetes.
  - Pods are now referred to as Apps.
  - App metrics are no longer reported.
- The UI now shows App information on the System Metrics page instead Pod information. No per-app metrics are displayed.

### Fixed
- Fixed bug where storage check was always getting 0% disk usage.

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
