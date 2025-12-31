# Changes on branch `lookup-table-last-modified`
Document any changes on this branch here.
### Added
- Added a `created` and `modified` field to the `lookup_tables` records and exposed them to the `lookup` query
- Added table name validation when creating lookup tables to prevent attempting create table with invalid names 
- Added table lookup validation step to reject `last_updated` in the column list to avoid conflicting with the autopopulated `last_updated` column

### Changed
- 

### Fixed
- Fixed an issue where updating plugins could lead two a false error about duplicate lookup tables across plugins

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Refactored the lookup query to use parameters and sanitize identifiers (i.e. column names) throughout the query

### Upgrade and Migration
- 
