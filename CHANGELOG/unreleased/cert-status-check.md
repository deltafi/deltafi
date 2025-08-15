# Changes on branch `cert-status-check`
Document any changes on this branch here.
### Added
- Added an SSL Secret check that checks the expiration date of the certificates loaded in the system
- Added the `checkSslExpirationErrorThreshold` system property, any certificates expiring within the threshold will cause the system to become unhealthy
- Added the `checkSslExpirationErrorThreshold` system property, any certificates expiring within the threshold will cause the system to become degraded

### Changed
- 

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
