# Changes on branch `fleet-diff-sorting`
Document any changes on this branch here.
### Added
- Added `SET` variable data type for order-independent collection properties. SET values are stored in sorted order, preventing false positives in fleet configuration diffs when the same values are entered in different order.

### Changed
- Changed `allowedAnalyticsAnnotations` and `provenanceAnnotationsAllowed` system properties from `LIST` to `SET` data type
- Collection property values (LIST, SET, MAP) are now displayed as pills instead of raw comma-separated strings

### Fixed
- Fixed list property editor creating duplicate entries when a value contains a comma

### Removed
-

### Deprecated
-

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
- Existing `allowedAnalyticsAnnotations` and `provenanceAnnotationsAllowed` property values are automatically normalized (sorted) on startup
