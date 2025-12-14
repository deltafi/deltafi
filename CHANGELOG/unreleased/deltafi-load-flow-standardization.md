# Changes on branch `deltafi-load-flow-standardization`
Document any changes on this branch here.
### Added
- Added `-o/--format` flag to `deltafi data-sink get` for output format consistency

### Changed
- `deltafi transform load` and `deltafi data-sink load` now use positional file arguments and support multiple files like `deltafi data-source load file1.json file2.json`

### Fixed
- `deltafi data-sink load` was broken due to missing flag registration

### Removed
-

### Deprecated
- `deltafi transform load -f` flag is deprecated; use positional arguments instead

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
-
