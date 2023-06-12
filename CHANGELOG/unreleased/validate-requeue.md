# Changes on branch `validate-requeue`
Document any changes on this branch here.
### Added
- 

### Changed
-

### Fixed
- The core now performs an extra check for every requeued DeltaFile to ensure it is not already in the queue. The redis ZSET already checks for exact matches, but in cases where a different returnAddress is used there were still opportunities for duplication. 

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
