# Changes on branch `normalized-df-data-source`
Document any changes on this branch here.
### Added
- 

### Changed
- Normalized `delta_files.data_source` from a text column to an integer foreign key referencing `flow_definitions`, significantly reducing index sizes

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
- Database migration V73 normalizes `delta_files.data_source` and rebuilds 5 indexes. On large tables (10M+ rows) this may take several minutes. Plan for downtime during this upgrade.
