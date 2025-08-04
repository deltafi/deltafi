# Changes on branch `ssl-management`
Document any changes on this branch here.
### Added
- Added graphql endpoints for managing SSL keys

### Changed
- Use a single ca-chain that can be used for keys

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
- The compose `cert` folder has new structure, `deltafi up` looks for the old setup and automatically migrates any existing key files
