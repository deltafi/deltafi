# Changes on branch `error-counting-bug`
Document any changes on this branch here.
### Added
-

### Changed
-

### Fixed
- Fixed error counting to count distinct DeltaFiles instead of DeltaFileFlows, preventing inflated counts when a DeltaFile has multiple errored flows
- Fixed "By Message" pagination showing incorrect totals (e.g., 43 rows instead of 4 unique messages)
- Fixed bug where output content appeared empty in DeltaFile viewer for flows with no actions

### Removed
-

### Deprecated
- `flow` and `type` fields on `CountPerMessage` GraphQL type (use nested `flows` field instead)

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
-
