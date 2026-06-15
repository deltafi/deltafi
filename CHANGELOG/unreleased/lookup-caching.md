# Changes on branch `lookup-caching`
Document any changes on this branch here.
### Added
- Added a `ttlSeconds` field to the lookup `LookupResults` type, indicating how long a client may cache the result before re-querying core. It is derived from the table's `refreshDuration` (and `lastRefresh` for supplier-fed tables); it is null when the table isn't cacheable (pull-through, or no `refreshDuration`).

### Changed
- Reworked lookup-table caching in the Java action-kit. The new cache honors the per-result `ttlSeconds`, so each table is cached only for its own refresh window. 
- Lookup result rows are now returned as unmodifiable maps.

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
