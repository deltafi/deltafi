# Changes on branch `analytic-dashboard-performance`
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
- Increase timescaleDB chunk size from 1 day to 1 week to ensure typical queries use a single chunk for better performance
- Improve Annotation by Data Source and Pivot by Annotation dashboard performance

### Upgrade and Migration
- To change the timescale chunk size the tables must be recreated and migrated. This can be a lengthy migration depending on the size of these tables.
