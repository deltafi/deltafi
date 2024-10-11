# Changes on branch `limit-join-schedule-job-runners`
Document any changes on this branch here.
### Added
- 

### Changed
- Only schedule the `handleTimedOutJoins` task to run when `schedule.maintenance` is true
- Schedule `handleTimedOutJoins` to run at a fixed delay of one second instead recalculating the next scheduled time
- Reject join configurations with a maxAge below one second

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
