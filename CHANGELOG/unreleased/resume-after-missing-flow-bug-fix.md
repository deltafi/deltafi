# Changes on branch `resume-after-missing-flow-bug-fix`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- If a transform action completes, but the transform flow is no longer running, the last action is marked as `ERROR` instead of generating a synthetic `MissingRunningFlow` action. This allows the DeltaFile to be resumed if the flow is restarted (which previously left the DeltaFile `IN_FLIGHT` indefinitely)

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
