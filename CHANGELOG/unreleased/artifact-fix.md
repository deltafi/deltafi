# Changes on branch `artifact-fix`
Document any changes on this branch here.
### Added
- `deltafi artifacts list` now supports a `--show-type` flag that adds a Content-Type column to the output.

### Changed
- 

### Fixed
- `deltafi artifacts publish --content-type <mime>` now stores the specified MIME type on the published version. Previously the flag was silently ignored and every uploaded artifact was recorded as `application/octet-stream` regardless of the value passed.

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
