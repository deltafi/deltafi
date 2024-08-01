# Changes on branch `docker-web-gui`
Document any changes on this branch here.
### Added
- Added deltafi/docker-web-ui container to the stack
- Compose stack: compose script has `logs`, `ps`, `top`, `pause`, and `unpause` commands

### Changed
- Compose stack: Remove mongo DB on uninstall, instead of dropping collections
- Compose stack: Compose script will remove mongo DB on uninstall
- Compose stack: Compose script will clean up plugin containers on uninstall

### Fixed
- Compose stack: Removed dependency on Ruby to run CLI

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
