# Changes on branch `ui-test-parallelization`
Document any changes on this branch here.
### Added
- 

### Changed
- Improved UI CI test pipeline performance with parallelization and early failure termination
- UI e2e tests now use retryable setup to handle transient initialization failures
- Added `runTest.sh` argument passthrough for running individual test specs

### Fixed
- Fixed race condition where the mock service worker may not be ready before the app fetches initial data

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
