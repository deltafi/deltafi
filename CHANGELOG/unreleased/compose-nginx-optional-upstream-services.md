# Changes on branch `compose-nginx-optional-upstream-services`
Document any changes on this branch here.
### Added
- Added error handling to NGINX in compose to show more a Service Unavailable page in the UI when upstream services can't be reached instead of the NGINX default error pages.

### Changed
- Changed the deltafi-core setup to automatically forward all unknown routes (i.e. 404s) to the UI. This allows new UI routes to automatically work and replaces the generic Whitelabel error page with the UI not found page

### Fixed
- Fixed an issue where NGINX had errors after an upgrade in compose. This change also allows NGINX to start without depending on any other services.

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
