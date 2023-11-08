# Changes on branch `core-thread-semaphore`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Improve core memory use when too many files are flowing through the system. Introduce a semaphore to limit how many messages will be pulled off the queue, configurable with the `coreInternalQueueSize` property.
- Do not start unneccessary processing threads in ingress.

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
