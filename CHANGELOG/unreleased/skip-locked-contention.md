# Changes on branch `fix/skip-locked-contention`
Document any changes on this branch here.
### Added
-

### Changed
-

### Fixed
- Fixed PostgreSQL deadlocks between auto-resume and delete operations by adding `FOR UPDATE SKIP LOCKED` to resume, content-delete, and bulk-delete queries, and converting per-row statements to single bulk operations

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
