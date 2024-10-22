# Changes on branch `345-int-test-new-endpoints`
Document any changes on this branch here.
### Added
- Added CLI commands `load-integration-test` and `run-integration-test`

### Changed
- Integration tests are now stored separately from their results with new GraphQL endpoints. This allows for test configurations to be saved once, and run later by using just the name
- Role `IntegrationTestLaunch` renamed to `IntegrationTestUpdate`

### Fixed
- 

### Removed
- Removed CLI command `integration-test`

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- New table `integration_tests` and column name change in `test_results`
- Integration test YAML now requires `name` field
