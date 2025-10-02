# Changes on branch `flexible-integration-test-annotation-matching`
Document any changes on this branch here.
### Added
- [integration test] New `KeyValueCheck` type used for annotations and metadata matches; now supports "exists" and regex matching

### Changed
- [integration test]  The data type for expected annotations and metadata in results classes was changed from `KeyValue` to `KeyValueChecks`

### Fixed
-

### Removed
- [integration test]  The `metaExactMatch` field in `ExpectedFlow` has been removed

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- Integration tests using metadata or annotation matching must migrate to new `KeyValueChecks` type.
- All entries in the `integration_tests` DB table are deleted during migration.
