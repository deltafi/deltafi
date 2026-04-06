# Changes on branch `plugin-env-vars`
Document any changes on this branch here.
### Added
- Added `deltafi.pluginEnv` to `site/values.yaml` to inject environment variables into all plugin containers/pods
- Added `pluginEnvVars` to the plugins query that is available to users with the `Admin` role
- Added a mutation `redeployAllPlugins` that creates a fresh deployment of all installed plugins

### Changed
- Changed `deltafi up` so it always redeploys existing plugins with the latest settings after a successful upgrade

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
