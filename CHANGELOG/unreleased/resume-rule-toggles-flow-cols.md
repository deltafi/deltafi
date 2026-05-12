# Changes on branch `resume-rule-toggles-flow-cols`
Document any changes on this branch here.
### Added
- Added an option to disable `ResumePolicies`. Disabling a policy prevents existing DeltaFiles from resuming based on that policy and newly errored DeltaFiles will not apply the policy. When the policy is enabled any previously scheduled resumes for the policy will fire if the DeltaFile is still eligible for resume.

### Changed
- Deleting a `ResumePolicy` now clears out the next auto resume fields for any DeltaFiles that were scheduled to be resumed based on the policy that was deleted

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
- Database migration v77 does the following
  - adds a new column to track the enabled flag on resume rules (fast migration)
  - add a new field to delta_file_flows to track the next_auto_resume_reason and indexes the field (this is a quick change on systems where there are not many errored flows waiting for auto-resume)
