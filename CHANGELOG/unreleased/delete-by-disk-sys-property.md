# Changes on branch `delete-by-disk-sys-property`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- 

### Removed
- Removed Disk Space delete policies and replaced them with a single system property, `diskSpacePercentThreshold`, to simplify configuration

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- This upgrade removes Disk Space delete policies. The `diskSpacePercentThreshold` system property will be set with the minimum global (i.e. no data source set) maxPercent found in the enabled Disk Space delete polices.
