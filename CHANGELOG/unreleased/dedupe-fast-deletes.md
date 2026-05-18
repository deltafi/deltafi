# Changes on branch `dedupe-fast-deletes`
Document any changes on this branch here.
### Added
- 

### Changed
- Re-queuing the same content deletion no longer creates duplicate pending work or inflates the pending-delete lag metric.

### Fixed
- On multi-node local object storage deployments, a fast-delete worker on one node could clear another node's pending deletion before that node had processed it, leaving the local file behind. Each worker now only clears its own pending work.

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
