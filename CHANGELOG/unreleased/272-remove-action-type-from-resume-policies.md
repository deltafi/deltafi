# Changes on branch `272-remove-action-type-from-resume-policies`
Document any changes on this branch here.
### Added
- 

### Changed
- Changed the unique constraint on resume_policies to be the combination of dataSource, errorSubstring, and action with consideration of absent (null) values

### Fixed
- Fixed resume policy constraint to require at least one of dataSource, errorSubstring, or action

### Removed
- Removed `actionType` criteria from resume policies (#272)

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- When reverting to an old snapshot, resume policies will not be restored due to constraint changes
