# Changes on branch `error-page-performance`
Document any changes on this branch here.
### Added
- Added `acknowledgeByFlow` and `acknowledgeByMessage` GraphQL mutations to acknowledge errors by flow or error message instead of by DID list
- Added `annotateByFlow` and `annotateByMessage` GraphQL mutations to annotate DeltaFiles by flow or error message instead of by DID list

### Changed
- Error summary queries no longer return DIDs via ARRAY_AGG, significantly improving query performance
- Errors page "All" tab now shows DeltaFiles with any flow in ERROR state, not just DeltaFiles with stage=ERROR. This allows users to see errors immediately even while other flows are still processing
- UI bulk actions (acknowledge, annotate) on the "By Flow" and "By Message" tabs now use the new filter-based mutations instead of passing DID lists

### Fixed
-

### Removed
-

### Deprecated
- `dids` field on `CountPerFlow` and `CountPerMessage` GraphQL types (now returns null)

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
-
