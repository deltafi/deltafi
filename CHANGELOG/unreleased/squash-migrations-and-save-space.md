# Changes on branch `squash-migrations-and-save-space`
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
- Squash flyway migrations
- Reduce storage size of tables in postgres. Reorder fields for byte alignment, use postgres enums, exclude nulls and blanks in jsonb, and abbreviate jsonb field names. 

### Upgrade and Migration
- Seamless upgrade is not possible since Flyway will remember the old migrations. In postgres run `delete from flyway_schema_history;` before upgrading.
