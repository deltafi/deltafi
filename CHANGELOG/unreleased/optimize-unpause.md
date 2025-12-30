# Changes on branch `optimize-unpause`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Fixed replay to queue files when flow is invalid or plugin is not ready (consistent with ingress behavior)
- Fixed publishing to queue files when the subscriber's plugin is not ready

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Optimize processing of unpaused DeltaFiles: reduce batch size and distribute action results across workers 

### Upgrade and Migration
- 
