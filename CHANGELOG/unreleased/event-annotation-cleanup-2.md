# Changes on branch `event-annotation-cleanup-2`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Fixed cleanup of event_annotations table. Deletes were not being properly triggered. Added maintenance job to regularly clean up rows. 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- Ensure processing is stopped before the migration runs to prevent lost analytics