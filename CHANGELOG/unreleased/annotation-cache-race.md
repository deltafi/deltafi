# Changes on branch `annotation-cache-race`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Analytics cache services (AnnotationKey, AnnotationValue, ErrorCause, EventGroup, ActionName) now cache integer IDs instead of managed Hibernate entities, preventing session corruption errors under high concurrency
- Annotation batch processing now handles individual annotation failures gracefully instead of losing the entire batch when one entry fails

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
