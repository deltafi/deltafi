# Changes on branch `stuck-paused`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Fixed data source flows that were paused after ingress completed but before publishing to subscribers
- Fixed requeue mechanism to persist error state when action configuration is not found
- Fixed requeue query to exclude actions in JOINING state

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Added partial index on paused DeltaFiles to improve unpause query performance

### Upgrade and Migration
- 
