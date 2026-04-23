# Changes on branch `refine-cold-queue-triggers`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Refined cold queue tracking triggers to use per-operation `WHEN` clauses and added repo-level trigger coverage so unrelated `delta_file_flows` updates do not dispatch cold queue bookkeeping.

### Upgrade and Migration
- 
