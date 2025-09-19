# Changes on branch `null-egress-behavior`
Document any changes on this branch here.
### Added
- 

### Changed
- Added `noContentPolicy` parameter HTTP Egress actions and `DeltaFiEgress` to configure behavior for egress when no content is present:
  - ERROR - (default) Action will error
  - FILTER - Action will filter
  - SEND_EMPTY - Action will publish with zero length content and empty string filename
- Improved error logging when Egress has no content and an ERROR policy

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
- If you have a custom Egress action, the action kit will no longer error automatically if there is no content available.
