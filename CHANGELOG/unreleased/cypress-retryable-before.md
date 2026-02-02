# Changes on branch `cypress-retryable-before`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Added guards to UI tests to prevent spurious timeouts and failures when under heavy load

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Moved UI viewport tests into Page Structure groups across all Cypress specs to share page loads and reduce test runtime

### Upgrade and Migration
- Upgraded MSW (Mock Service Worker) from v1 to v2 for UI test mocking
