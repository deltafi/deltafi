# Changes on branch `error-filter-tracking`
Document any changes on this branch here.
### Added
- 

### Changed
- The Errors and Filtered summary views (count-by-flow and count-by-message) now report a count of errored or filtered flow executions instead of distinct DeltaFiles. A DeltaFile with errors on multiple flows or multiple error messages is now reflected in each matching row.

### Fixed
- Errors and Filtered summary views no longer slow down or time out when the backlog grows into the hundreds of thousands. Latency is now flat regardless of how many errors are pending.

### Removed
- 

### Removed
- The date-range picker on the Errors, Filtered, and Fleet Errors pages. These views now always reflect the full current backlog, removing a common source of surprise when errors appeared to be missing because of the default date window.

### Deprecated
- `modifiedAfter` and `modifiedBefore` on the `errorSummaryByFlow`, `errorSummaryByMessage`, `filteredSummaryByFlow`, and `filteredSummaryByMessage` GraphQL queries. Supplied values are accepted for backward compatibility but no longer narrow the result.

### Security
- 

### Tech-Debt/Refactor
- Improved concurrency in the `stressTest` mutation.

### Upgrade and Migration
- 
