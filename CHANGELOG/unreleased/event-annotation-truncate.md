# Changes on branch `event-annotation-truncate`
Document any changes on this branch here.
### Added
- 

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
- *NOTE: POTENTIAL METRICS LOSS!*
  - A previous bug that was not maintaining the `event_annotations` analytics table correctly might leave the system with an excessively large table that cannot be pruned in a timely manner by the cleanup task. A migration was added to truncate the table, which could cause metrics to be lost for any In-flight DeltaFiles when upgrading. To avoid losing metrics for In-flight DeltaFiles, disable ingress when upgrading, and allow all DeltaFiles to complete before starting the upgrade. Be sure to re-enable ingress after the upgrade is finished.
  - Additionally, any errored DeltaFiles that are resumed/egressed after the upgrade/migration will not have any egress annotation metrics.