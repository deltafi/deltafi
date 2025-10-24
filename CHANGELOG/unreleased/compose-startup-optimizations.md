# Changes on branch `compose-startup-optimizations`
Document any changes on this branch here.
### Added
- 

### Changed
- Changed the deltafi-core startup probe to use the versions endpoint to speed up the response
- Changed the health-checks in compose to reduce the time waiting for containers to start
- Changed the loki settings in compose to reduce the loki container startup time

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Adjusted the GraphQL schema file extensions to speed up the `deltafi-core` startup 

### Upgrade and Migration
- 
