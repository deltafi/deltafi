# Changes on branch `snapshot-import-and-restore`
Document any changes on this branch here.
### Added
- Added a mutation, `importSnapshotAndReset`, that allows operators to import and apply a snapshot in a single request
- Added a `--restore` flag to the `deltafi snapshot import` command to allow importing and restoring the snapshot in a single command

### Changed
- Changed the `--hard` flag to be true by default on the `deltafi snapshot restore` command to match the backend and GUI behavior  

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
