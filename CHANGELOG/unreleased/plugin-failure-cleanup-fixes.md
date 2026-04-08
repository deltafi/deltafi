# Changes on branch `plugin-failure-cleanup-fixes`
Document any changes on this branch here.
### Added
- 

### Changed
- In TimedDataSource, the `timedIngressAction` GraphQL field now allows null for pending flows during a plugin install

### Fixed
- When a snapshot revert fails to find an image, any associated flows left behind can now be cleared by removing the failed plugin

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- Database migration V71 adds `snapshot_coordinates` column to `plugins` table (quick)
