# Changes on branch `data-sink-kill-switch`
Document any changes on this branch here.
### Added
- Added a system property, `egressEnabled`, that can be used to turn off DeltaFile egress globally. When false, egress is disabled, pausing all DeltaFiles at `DataSinks`. Egress action queues are drained and the actions are ineligible for requeue until egress is re-enabled. Resetting the property to true will allow the actions to requeue.

### Changed
- Changed resume to skip egress action errors when egress is disabled, this applies to auto-resume rules as well

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
