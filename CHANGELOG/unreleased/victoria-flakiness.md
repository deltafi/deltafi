# Changes on branch `victoria-flakiness`
Document any changes on this branch here.
### Added
-

### Changed
- Increased VictoriaMetrics memory limit from 1GB to 2GB and CPU limit from 500m to 2 cores
- Increased VictoriaMetrics concurrent request limit from 2 to 8 and queue duration from 10s to 30s

### Fixed
- Fixed metric loss when VictoriaMetrics is unreachable: counters are now only decremented after successful transmission

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
