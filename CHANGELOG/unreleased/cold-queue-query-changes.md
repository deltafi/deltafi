# Changes on branch `cold-queue-query-changes`
Document any changes on this branch here.
### Added
- Added a column, `cold_queued_action` to the delta_file_flows to track that action that is cold_queued.

### Changed
- Changed the `cold_queue` index to include the `delta_file_id` to improve the cold queue query performance.

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
- Note - if you upgrade your system while it has a large number of cold queued DeltaFiles, the migration may take several minutes to complete
