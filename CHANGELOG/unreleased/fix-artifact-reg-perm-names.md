# Changes on branch `fix-artifact-reg-perm-names`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Now properly creating the user roles for accessing the artifact registry: `ArtifactRegistryRead`, `ArtifactRegistryWrite`

### Removed
- The names for the artifact registry read and write roles incorrectly had the same vlaue (`Artifacts`), which is now removed

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- - Database migration V79 removes the user role `Artifacts`
 
