# Changes on branch `66-cli-changes-for-compose`
Document any changes on this branch here.
### Added
- Support the following commands when running with docker-compose
   - install
   - uninstall
   - mongo-migrate
   - minio-cli
   - secrets
- Added a stop-service function to the compose script that can be used to stop individual containers

### Changed
- Use the `api/v1/versions` endpoint to get the running versions instead of relying on k8s

### Fixed
- Secret env variables are no longer quoted when they are generated to support the `minio-cli` command
- Fixed the check used to determine if there are mongo collections to drop on uninstall

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
