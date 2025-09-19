# Changes on branch `on-error-ds-add-meta-prefix-option`
Document any changes on this branch here.
### Added
- Configuration of an `OnErrorDataSource` now allows a `sourceMetadataPrefix` to be set. This is used as the prfix for any matching  metadata added from `includeSourceMetadataRegex`

### Changed
- 

### Fixed
- 

### Removed
- The default prefix for metadata added by an `OnErrorDataSource` -- `"source."` has been removed.

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- If you have any existing OnErrorDataSource where metadata copied from the source is expected to be prefixed with `"source."`, you will need to manually set the `sourceMetadataPrefix` in your flow plan
