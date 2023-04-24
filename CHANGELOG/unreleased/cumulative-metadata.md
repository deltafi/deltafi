# Changes on branch `cumulative-metadata`
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
- Make DeltaFile metadata accumulate as it travels through Transform and Load Actions.  Transform and Load Actions receive the original metadata plus any metadata that has been added by other actions that proceed it.  Metadata produced by a Format Action is still received by Validate and Egress Actions as it was sent, not including the metadata of any other actions that proceeded it.
- Remove sourceMetadata from ActionInput interfaces.

### Upgrade and Migration
- 
