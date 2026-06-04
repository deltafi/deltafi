# Changes on branch `gui-uuid-regex-bug`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Fixed UUID validation in the GUI DeltaFile Viewer and Import Metadata dialog rejecting valid DIDs. The validation only accepted UUID versions 0-5, but DeltaFi generates UUIDv7 DIDs (e.g. for joined DeltaFiles), so those DIDs could not be entered manually.

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
