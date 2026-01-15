# Changes on branch `add-local-content-storage-env-to-worker`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Fix cluster deployment for deltafi-core-worker to set the `LOCAL_STORAGE_CONTENT` environment variable so local storage check for content can be properly determined. This allows the storatge limit check by the ingress service to be skipped when external content storage is being used

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
