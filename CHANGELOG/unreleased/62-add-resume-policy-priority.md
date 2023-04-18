# Changes on branch `62-add-resume-policy-priority`
Document any changes on this branch here.
### Added
- New `priority` field in `ResumePolicy`, which is automatically computed if not set

### Changed
- Resume policy search is now in `priority` order (the highest value first)
- The resume policy name stored in the DeltaFile `nextAutoResumeReason` is no longer cleared when the DeltaFile is resumed

### Fixed
- Resume policy search will consider the number of action attempts and the policy `maxAttempts` during the policy search iteration loop, not after finding the first match

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- Added `priority` to `resumePolicy` collection
