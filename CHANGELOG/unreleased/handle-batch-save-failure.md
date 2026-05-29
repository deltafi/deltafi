# Changes on branch `handle-batch-save-failure`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Fixed a requeue stall where the core scheduler's batch save of cold-to-warm, timed-requeue, rate-limited, and unpaused DeltaFiles would roll back in full whenever a single file in the batch was modified concurrently (common for forking DeltaFiles whose sibling flow is processed while another flow is requeued). The batch re-selected the same files and failed every cycle, making no progress. The scheduler now degrades to saving and dispatching each file individually when a batch save hits an optimistic-lock conflict, so one stale file no longer blocks the rest of the batch.

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
