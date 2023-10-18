# Changes on branch `long-running-requeue-prevention`
Document any changes on this branch here.
### Added
- 

### Changed
- Reduce default requeue time from 300 to 30 seconds. This was previously raised to mitigate double-queuing issues when an Action took longer than the requeue period to process.

### Fixed
- Do not issue automatic requeues for long-running tasks that are still being processed by an Action

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- It is recommended in most circumstances that you set your requeueSeconds System Property to 30 if it was previously set to something higher.
