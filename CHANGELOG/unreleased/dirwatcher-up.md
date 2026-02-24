# Changes on branch `dirwatcher-up`
Document any changes on this branch here.
### Added
- Added comprehensive test coverage for deltafi-dirwatcher (http transport, config, delay queue, and watcher)

### Changed
-

### Fixed
- Fixed settling time validation in dirwatcher to properly compare time.Duration values

### Removed
-

### Deprecated
-

### Security
-

### Tech-Debt/Refactor
- Modernized dirwatcher code to use `any` instead of `interface{}`, `min()` builtin, and `slices.Contains()`

### Upgrade and Migration
-
