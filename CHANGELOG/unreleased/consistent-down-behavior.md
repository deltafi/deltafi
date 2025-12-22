# Changes on branch `consistent-down-behavior`
Document any changes on this branch here.
### Added
- 

### Changed
- `deltafi down` now uninstalls DeltaFi helm charts but leaves the KinD cluster running. Use `deltafi down -d` to destroy the cluster and data directory. This makes KinD behavior consistent with Compose (non-destructive by default).

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
