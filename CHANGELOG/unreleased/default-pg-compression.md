# Changes on branch `default-pg-compression`
Document any changes on this branch here.
### Added
- 

### Changed
- PostgreSQL `wal_compression` now defaults to `lz4` on both Kubernetes and Compose deployments, reducing WAL write latency and relieving WALWrite contention under high write throughput.

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
