# Changes on branch `plugin-reconciliation`
Document any changes on this branch here.
### Added
- Async plugin reconciliation service that manages plugin desired state vs actual state
- Plugin installation states: PENDING, INSTALLING, INSTALLED, FAILED, REMOVING
- Rollback support for failed plugin upgrades when a previous version was successful
- Disable/enable plugin functionality to stop plugins without losing configuration
- TUI: `deltafi plugin list` now shows plugin installation status and rollback availability
- TUI: `deltafi plugin disable <name>` to disable a plugin
- TUI: `deltafi plugin enable <name>` to enable a disabled plugin
- TUI: `deltafi plugin rollback <name>` to rollback a failed upgrade
- TUI: `deltafi plugin retry <name>` to retry a failed installation
- Flow behavior now considers plugin state: data queues during plugin upgrades instead of being rejected
- Plugin icon with tooltip on Data Sources and Data Sinks pages (links to plugin details like Transforms page)

### Changed
- Plugin installation is now asynchronous - returns immediately after queuing
- Invalid flows now queue data instead of rejecting it (data resumes when flow becomes valid)
- Invalid flows can now be paused/stopped without first resolving validation errors
- Flows with unavailable plugins (PENDING/INSTALLING/FAILED) now queue data instead of rejecting it
- Only explicitly STOPPED flows or Disabled plugins reject data; all other unavailability queues data
- Flow tooltips now show "(data flow paused)" when plugin is unavailable

### Fixed
- Docker: Leftover containers are removed before plugin install to prevent 409 Conflict errors
- Snapshot restore now correctly identifies plugin upgrades vs new installs by image name

### Removed
- `pluginAutoRollback` property - rollback is now manual via `deltafi plugin rollback <plugin>`

### Deprecated
-

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
-
