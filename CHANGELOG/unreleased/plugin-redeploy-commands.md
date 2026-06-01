# Changes on branch `plugin-redeploy-commands`
Document any changes on this branch here.
### Added
- Added a new mutation that redeploys an installed plugin (useful when plugin environment variables are updated and need to be pushed out)
   ```graphql
   mutation RedeployPlugin($groupId: String!, $artifactId: String!, $version: String!) {
     redeployPlugin(pluginCoordinates: { groupId: $groupId, artifactId: $artifactId, version: $version }) {
       success
         info
         errors
       }
   }
   ```
- Added a new command, `deltafi plugin redeploy`, that is used to redeploy a plugin 
- Added a `redeploy plugin` option in the GUI Plugins page in the plugin context menu

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
- 
