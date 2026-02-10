# Changes on branch `duckdb-up`
Document any changes on this branch here.
### Added
- Added comprehensive tests for DuckDB MAP/annotation access patterns in deltafi-analytics

### Changed
- Analytics: Updated DuckDB MAP access syntax for v1.2+ compatibility (map['key'] now returns value directly instead of list)

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
- Upgraded deltafi-analytics DuckDB driver from github.com/marcboeker/go-duckdb v1.8.5 to github.com/duckdb/duckdb-go v2.5.5
- Upgraded deltafi-analytics parquet-go from v0.26.0 to v0.27.0
