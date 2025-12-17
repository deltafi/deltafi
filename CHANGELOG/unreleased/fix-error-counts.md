# Changes on branch `fix-error-counts`
Document any changes on this branch here.
### Added
-

### Changed
- Bulk operations (resume, acknowledge, annotate) now show real-time progress with percentage and count
- Bulk operations can be stopped mid-operation with a Stop button
- Modify Metadata dialogs for resume and replay now show blank inputs for batch operations instead of attempting to aggregate metadata across all DeltaFiles
- `ResumeMetadata` input type now has optional `flow` and `action` fields; when null, metadata applies to all errored flows/actions

### Fixed
- Fixed errors badge in sidebar showing incorrect count (capped at 10k) instead of actual error count

### Removed
-

### Deprecated
- `errorMetadataUnion` and `sourceMetadataUnion` GraphQL queries (use `deltaFile` query for single DeltaFile metadata)

### Security
-

### Tech-Debt/Refactor
- Cached total unacknowledged error count in ErrorCountService to avoid redundant database queries
- Added `limit` parameter to bulk mutation GraphQL APIs to enable client-controlled batching

### Upgrade and Migration
-
