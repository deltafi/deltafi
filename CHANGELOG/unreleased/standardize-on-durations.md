# Changes on branch `standardize-on-durations`
Document any changes on this branch here.
### Added
- 

### Changed
- Changed the requeueSeconds property to requeueDuration
- Changed the deltaFileCache.syncSeconds property to deltaFileCache.syncDuration

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
- The migrate-to-durations.js script (automatically run during installation) updates the deltaFiProperties and
systemSnapshot Mongo database collections with the following:
  - Replaces requeueSeconds with requeueDuration of "PT{requeueSeconds}S"
  - Replaces deltaFileCache.syncSeconds with deltaFileCache.syncDuration of "PT{deltaFileCache.syncSeconds}S"
  - Replaces REQUEUE_SECONDS with REQUEUE_DURATION in the setProperties array
  - Replaces DELTAFILE_CACHE_SYNC_SECONDS with DELTA_FILE_CACHE_SYNC_DURATION in the setProperties array
  - Replaces DELTA_FILE_CACHE_SYNC_SECONDS with DELTA_FILE_CACHE_SYNC_DURATION in the setProperties array
