# Changes on branch `diskspace-use-modified-query`
Document any changes on this branch here.
### Added
- Add inFlight, terminal, and contentDeleted properties to DeltaFiles for more efficient indexing

### Changed
- Disk space delete policies sort by modified date instead of created date

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Optimize indexes to improve performance of Mongo deltafile delete queries

### Upgrade and Migration
- Ensure ingress is disabled and all files are at rest before beginning upgrade, as the migration is slow and must update all DeltaFiles
