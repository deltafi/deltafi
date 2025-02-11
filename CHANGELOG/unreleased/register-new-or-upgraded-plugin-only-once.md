# Changes on branch `register-new-or-upgraded-plugin-only-once`
Document any changes on this branch here.
### Added
- 

### Changed
- Only register a new or upgraded plugin once; it does not need to be repeated for every pod or when pods restart

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
- Added `registration_hash` column to `plugins` Postgres table
