# Changes on branch `read-receipts-not-being-recorded`
Document any changes on this branch here.
### Added
- 

### Changed
- DeltaFiles may be marked COMPLETE when there are pending annotations, but will not be terminal

### Fixed
- Read receipts (pending data-sink annotations) are now saved to the DeltaFile

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- The `queued_annotations` table will be cleared
