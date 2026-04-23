# Changes on branch `requeue-query-optimization`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Rewrote the requeue scan so it uses the `idx_delta_file_flows_requeue` partial index. Under large IN_FLIGHT backlogs the planner had been choosing a full-index scan on the generic `delta_file_id` index, causing per-run requeue times to balloon to tens of seconds; the new form completes in under a second against ~1.5M rows.

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
