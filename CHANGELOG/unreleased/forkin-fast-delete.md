# Changes on branch `forkin-fast-delete`
Document any changes on this branch here.
### Added
- Added support for multiple concurrent fast delete workers per node to improve deletion throughput
  - Kubernetes: Configure via `deltafi.fastdelete.workersPerNode` in Helm values (default: 1)
  - Docker Compose: Set `FASTDELETE_WORKERS` environment variable (default: 1)

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
- 

### Upgrade and Migration
- 
