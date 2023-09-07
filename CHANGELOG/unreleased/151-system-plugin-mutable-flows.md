# Changes on branch `151-system-plugin-mutable-flows`
Document any changes on this branch here.
### Added
- Added a `system-plugin` where flows and variables can be added and removed without a full plugin install
- Added a mutation, `removePluginVariables`, to remove variables from the system-plugin

### Changed
- Limit flow plan mutations to the system plugin. Attempting to add or remove flow plans to a plugin other than the system plugin will result in an error
- Change the `savePluginVariables` mutation to take a list of variables that is always added to the `system-plugin`

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
