# Changes on branch `allow-hikari-idle`
Document any changes on this branch here.
### Added
- 

### Changed
- Core database connection pools now open connections on demand instead of pre-allocating the full pool at startup, reducing idle Postgres connection usage and avoiding `max_connections` saturation in larger deployments.

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
