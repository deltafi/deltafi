# Changes on branch `in-mem-s3`
Document any changes on this branch here.
### Added
- Added `deltafi.storage.inMemory` option to `site/values.yaml` to run s3proxy with in-memory storage instead of filesystem storage. When enabled, content is stored in s3proxy's memory and content deletion uses the S3 API directly instead of the filesystem-based fast delete process.

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
-

### Upgrade and Migration
-
