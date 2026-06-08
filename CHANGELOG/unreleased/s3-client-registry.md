# Changes on branch `s3-client-registry`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Fixed an issue where S3 clients were rebuilt on every action invocation (a params equality bug defeated caching) and recreated hourly without being closed, leaking connections. They are now reused per configuration and closed on shutdown.

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
