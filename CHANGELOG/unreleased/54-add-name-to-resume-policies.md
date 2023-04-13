# Changes on branch `54-add-name-to-resume-policies`
Document any changes on this branch here.
### Added
- New required field `name` to auto-resume policies
- New field `nextAutoResumeReason` set to auto-resume policy name when policy is applied

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
- Added `name` field to `resumePolicy` collection
- Added `name` field to `resumePolicies` in `systemSnapshot` collection
- Added `nextAutoResumeReason` field to `deltaFile` collection where `nextAutoResume` is set
