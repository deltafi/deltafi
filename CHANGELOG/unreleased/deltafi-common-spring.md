# Changes on branch `deltafi-common-spring`
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
- Remove all spring dependencies from `deltafi-common` moving them to `deltafi-common-spring`. The `deltafi-common` package is now just the type definitions and common logic shared between core and the action-kit.
- Move `NiFi` specific code out of `deltafi-common` to prevent including it in all java plugins

### Upgrade and Migration
- 
