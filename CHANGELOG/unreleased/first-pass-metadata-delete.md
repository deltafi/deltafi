# Changes on branch `first-pass-metadata-delete`
Document any changes on this branch here.
### Added
- Add a first pass delete when metadata is being deleted to remove any metadata that does not have associated content (because it was already deleted by another delete policy) 

### Changed
- No longer guarantee order deltaFiles are deleted in.  Previously the removals were sorted by modified date, now deletes will happen in random order, to improve performance.

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
