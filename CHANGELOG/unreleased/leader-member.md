# Changes on branch `leader-member`

### Added
- Added leader-member monitoring feature for centralized monitoring of distributed DeltaFi deployments. See [Leader-Member Deployment](advanced/leader_member.md) for details.
  - Configure member sites via `leaderConfig` system property with URL, tags, and credentials
  - Leader Dashboard page displays member health, error counts, queue metrics, and system metrics
  - Multiple dashboard views: Summary, Detailed, Ingress, Egress, Storage, Deleted bytes
  - Fleet Config page compares plugins, flows, properties, and other configuration across members
- Added `/api/v2/status/report` endpoint combining status, metrics, and version in a single response
- Added `/api/v2/metrics/flow` endpoint for querying ingress/egress/storage/deleted byte metrics
- Added `/api/v2/system/snapshot/current` endpoint to retrieve current configuration as a snapshot
- UI conditionally shows Leader Dashboard and Fleet Config menu items only when leader mode is enabled
- Added warm queue, cold queue, and paused metrics to the dashboard

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
-

### Upgrade and Migration
-
