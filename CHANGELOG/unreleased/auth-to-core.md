# Changes on branch `auth-to-core`
Document any changes on this branch here.
### Added
- 

### Changed
- Creating a user with the DN set or updating a user DN will always update the username to the CN
- The `Admin` permission will always be added back to the `Admin` user on restart if it was removed

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Moved authentication from the standalone `deltafi-auth` project into `deltafi-core`

### Upgrade and Migration
- 
