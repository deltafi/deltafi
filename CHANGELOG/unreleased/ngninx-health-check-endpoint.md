# Changes on branch `ngninx-health-check-endpoint`
Document any changes on this branch here.
### Added
- Added a dedicated `/health` endpoint to nginx to determine if nginx is accepting connections

### Changed
- Enabled HTTP/1.1 keepalive connections between nginx and upstream services in compose to mirror the Kubernetes settings

### Fixed
- Removed redundant auth check from the http redirect configuration

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
