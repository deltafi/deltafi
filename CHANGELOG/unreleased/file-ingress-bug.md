# Changes on branch `file-ingress-bug`
Document any changes on this branch here.
### Added
- Dirwatcher will respond to DIRWATCHER_SETTLING_TIME environment variable to set the polling period for accepting a file

### Changed
- File ingress service renamed dirwatcher
- Dirwatcher will periodically sweep watch dirs to pick up files that failed to generate os filesystem events

### Fixed
- Fixed issue where dirwatcher would prematurely pick up a partial file and publish it

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
