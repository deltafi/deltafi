# Changes on branch `check-ingress-and-plugin-state-before-running-int-test`
Document any changes on this branch here.
### Added
- 

### Changed
- When an integration test cannot be started, record the INVALID test result in the DB. This provides attempt history
and works better with the TUI.

### Fixed
- Generate an error when attempting to run an integration test if system ingress is disabled, any of the required plugins are disabled, or any of the required plugins were not successfully upgraded

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
