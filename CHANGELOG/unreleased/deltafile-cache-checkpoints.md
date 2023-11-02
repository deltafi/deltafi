# Changes on branch `deltafile-cache-checkpoints`
Document any changes on this branch here.
### Added
- 

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
- Improve DeltaFile cache flushing.  Flush to the database at the configured deltaFileCache.syncSeconds interval.  Previously it was only flushing if the modified time was greater than this interval, but now it checks against the last database sync time.  This fixes a race with requeues that would often cause Mongo OptimisticLockingExceptions and duplicated work.

### Upgrade and Migration
- 
