# Changes on branch `blocking-timed-ingress`
Document any changes on this branch here.
### Added
- 

### Changed
- Timed Ingress Action Improvements
  - Return results synchronously
  - Block execution until the last result is returned, or until the task is lost
  - Provide a memo field in the context and result, so bookmarks/notes can be passed to the next execution
  - Add result option to begin the next execution immediately, without waiting for the usual interval
  - Add result status and statusMessage
  - Respect maxErrors
  - Add documentation
  - Add python implementation

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
