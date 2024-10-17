# Changes on branch `remove-ingress`
Document any changes on this branch here.
### Added
- 

### Changed
- Rename core to core-scheduler 

### Fixed
- 

### Removed
- Remove separate ingress deployment, have core workers handle ingress and immediately affinitize and cache DeltaFiles
- Remove option to turn off the DeltaFileCache

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Speed up single DeltaFile inserts

### Upgrade and Migration
- 
