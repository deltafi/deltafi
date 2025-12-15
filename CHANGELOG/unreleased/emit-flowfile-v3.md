# Changes on branch `emit-flowfile-v3`
Document any changes on this branch here.
### Added
- Added `flowFileVersion` parameter to FlowfileEgress action (V1 default, V3 available)
- Added `/capture` endpoint to deltafi-egress-sink for saving egressed files to disk without requiring headers

### Changed
-

### Fixed
-

### Removed
-

### Deprecated
- FlowfileEgress V1 format - V3 will become the default in a future version

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
- FlowfileEgress action defaults to V1 format for backward compatibility (no action required)
- Recommended: Set `flowFileVersion: V3` in FlowfileEgress action configs for improved efficiency
