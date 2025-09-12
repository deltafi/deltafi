# Changes on branch `action-content-npe-fix`
Document any changes on this branch here.
### Added
- 

### Changed
- Changed the `ErrorResult.logErrorTo` method to include the `action` and `did` as json fields in the log entry

### Fixed
- Fixed an issue where failing to convert an action result to an `ActionEvent` caused DeltaFiles to get stuck In-Flight and treated as a long-running action
- Fixed an issue where null `ActionContent` entries caused an exception when creating `ActionEvents` from action results

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
