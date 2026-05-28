# Changes on branch `requeue-backpressure`
Document any changes on this branch here.
### Added
- New `requeueBacklogThreshold` property (Performance Controls, default 5000): when the shared result queue exceeds this depth, requeue and cold-to-warm scheduling pause until it drains, preventing the queue from outrunning what the cluster can process.

### Changed
- Requeued and cold-to-warm work is now distributed across all core workers instead of being routed back to the instance that scheduled it, removing a single-instance bottleneck during recovery.

### Fixed
- Fixed a feedback loop where a large action backlog caused the scheduler to repeatedly re-dispatch work and hammer the database, growing the queue instead of recovering.

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
