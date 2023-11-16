# Changes on branch `clickhouse-etl-bug`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- ETL was syncing to the most recent deltafile in the clickhouse database for the timestamp for beginning Mongo deltafile queries.  In the case where surveys were added while the ETL job was down, it woould catch up from the latest survey timestamp instead of the latest regular deltafile timestamp.

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
