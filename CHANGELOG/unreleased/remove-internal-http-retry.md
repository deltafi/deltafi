# Changes on branch `remove-internal-http-retry`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Remove internal retry from Http egress actions, fixing stream reuse issues. External Auto Resume should be used instead. 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- HttpEgressParameters and DeltaFiEgressParamters removed: retryCount, retryDelayMs
