# Changes on branch `prioritize-disk-delete-policies`
Document any changes on this branch here.
### Added
- 

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
- When mongo or the core are busy for long periods of time due to bursts of traffic, timed delete policies can fall behind. Ensure that disk delete policies rerun in between batches of timed deletes.

### Upgrade and Migration
- 
