# Changes on branch `118-pause-resume-commands`
Document any changes on this branch here.
### Added
- Added a `deltafi disable` command that disables all DeltaFi processes
- Added a `deltafi reenable` command that reenables all DeltaFi processes
- Added a `deltafi scale` command that allows you to edit the replica counts across all deployments and statefulsets and then apply the changes

### Changed
- Renamed the `compose stop` command to `compose uninstall`
- Renamed the `compose stop-service` command to `compose stop-services`

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Removed items from the deltafi-cli/config.template that should not be configurable 

### Upgrade and Migration
-