# Changes on branch `31-auth-migration`
Document any changes on this branch here.
### Added
- 

### Changed
- Database migrations in auth are now completed before workers are spawned.
- Auth `WORKERS` are now set to `8` by default.

### Fixed
- Fixed issue preventing auth `WORKERS` being set to greater than one.

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
