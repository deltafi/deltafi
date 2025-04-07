# Changes on branch `cold-queue-fixes`
Document any changes on this branch here.
### Added
- 

### Changed
- Updated the cache to write cold queued DeltaFiles to disk immediately and remove them from the cache to prevent conflicts when the scheduled cold queue task runs

### Fixed
- Fixed an issue where syncing the cache to disk could save outdated copies of the DeltaFile, causing unexpected action exceptions
- Fixed an issue where the ActionInput created by the requeue of cold queued DeltaFile was still marked as cold queued preventing it from writing to valkey
- Fixed an issue where the StateMachine was using the action name instead of queue name (action type) to check if the next action should be cold queued

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
