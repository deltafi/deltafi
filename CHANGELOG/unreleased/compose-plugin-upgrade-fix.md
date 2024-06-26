# Changes on branch `compose-plugin-upgrade-fix`
Document any changes on this branch here.
### Added
- Healthcheck for plugins installed in compose, the installation is not considered complete until the container reaches a healthy state
- Added rollback logic for failed plugin installs in compose
- Added restart policies for compose services

### Changed
- 

### Fixed
- Fixed bug that prevented plugins from being upgraded when running in compose

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
