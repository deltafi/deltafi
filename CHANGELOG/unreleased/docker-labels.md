# Changes on branch `docker-labels`
Document any changes on this branch here.
### Added
- Plugin container images are now labeled with `org.deltafi.version` reflecting the plugin version. Plugin authors can attach additional labels through the Gradle `docker { }` block using `label 'key', 'value'` for a single label or `labels([...])` for a map of labels. Label keys are validated against Docker's naming convention.

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
