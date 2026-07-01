# Changes on branch `provenance-foofoo`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Provenance now records a `FILTERED` final state for DeltaFiles filtered by a data sink in test mode, instead of leaving them stuck at `IN_FLIGHT`.
- Provenance now records a `COMPLETE` final state for DeltaFiles that egressed while awaiting expected annotations, instead of the internal `PENDING_ANNOTATIONS` value.

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
