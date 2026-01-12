# Changes on branch `authorizer-fixes`
Document any changes on this branch here.
### Added
- Added unit tests for `EntityAuthorizationResolver` covering enabled/disabled states, authorization granted/denied scenarios, HTTP failures, interruptions, and invalid JSON responses

### Changed
- Changed default entity-authorizer port from 8080 to 8081 to avoid conflicts with entity-resolver when both run in the same Kubernetes cluster
- Updated entity-authorizer default endpoint from `/` to `/api/v1/authorize` in documentation and configuration defaults
- Improved entity-authorizer configuration examples in values files with sample `auth.yaml` structure
- Added `config` sections to both `entityResolver` and `entityAuthorizer` in Helm values for injecting configuration files

### Fixed
- Fixed log message format string in `EntityAuthorizationResolver` where placeholder order was incorrect (`{} access{} using` → `{} access using {}{}`)
- Fixed docker-compose volume mount using wrong secret variable (`DELTAFI_SECRET_ENTITY_RESOLVER_SSL` → `DELTAFI_SECRET_ENTITY_AUTHORIZER_SSL`)
- Fixed null action index handling in `DeltaFileFlow.contentAtOrBefore()` for data sources - now correctly retrieves metadata from the last completed action instead of failing when actionIndex is null

### Removed
-

### Deprecated
-

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
- Entity authorizer deployments should update their configuration to use port 8081 (or explicitly configure the URL in site values)
