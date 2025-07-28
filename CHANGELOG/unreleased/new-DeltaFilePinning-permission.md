# Changes on branch `new-DeltaFilePinning-permission`
Document any changes on this branch here.
### Added
- Created a new permission `DeltaFilePinning`

### Changed
- Updated the `pin` and `unpin` mutations to requrie the `DeltaFilePinning` permission (was `DeletePolicyDelete`)

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
- Installer should add the new permission `DeltaFilePinning` to the approprirate roles
