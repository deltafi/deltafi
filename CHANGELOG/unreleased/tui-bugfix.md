# Changes on branch `tui-bugfix`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- TUI: fixed dirwatcher permission issue

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- If you have a data/dirwatcher directory created with root user/group, you should do one of the following:
  - chown -R the directory to make it match your user privs
  - remove the directory prior to upgrade (and DeltaFi will recreate it correctly)
  - remove the directory after upgrade and run `deltafi up` to recreate it correctly
