# Changes on branch `prevent-malformed-deltafiles`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Remove DeltaFile from the cache when an unhandled exception occurs to prevent inconsistent state from being saved
- Fix a race condition where missing or inactive data sinks were not properly handled
- Fix an issue that would prevent a DeltaFile from completing other transforms when one forked transform has errored

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
