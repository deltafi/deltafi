# Changes on branch `cap-redis-queue-size`
Document any changes on this branch here.
### Added
- Cap size of redis queues. If a redis queue exceeds a configured maximum size, new actions are placed in a "cold queued" state until the pressure has been relieved.

### Changed
- 

### Fixed
- The stressTest endpoint has been fixed to work with transform flows.

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
