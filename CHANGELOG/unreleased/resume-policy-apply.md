# Changes on branch `resume-policy-apply`
Document any changes on this branch here.
### Added
- New mutation `applyResumePolicies` allows recently added auto resume policies to be retroactively applied to any oustanding DeltaFiles in the ERROR stage (whicn are still resumable)
- New user role `ResumePolicyApply` in the `Resume Policies` group grants permisson to execute the `applyResumePolicies` mutation

### Changed
- Clarified documentration that the `flow` in an auto resume policy refers to the DeltaFile's sourceInfo flow. I.e., the ingress or transformation flow name

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
