# Changes on branch `disk-delete-resiliency`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Throw exception and do not trigger disk space delete policies if the API reports 0 byte disk usage or total size
- Stop deleting batches in the disk space delete policy if the target disk space threshold has been reached

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
