# Changes on branch `snapshot-flows-store-plugin`
Document any changes on this branch here.
### Added
- Added `placeholder` field to `FlowStatus` to identify placeholder flows awaiting plugin installation. Placeholder flows cannot be cloned.

### Changed
- Made DataSink `egressAction` nullable in GraphQL schema to support placeholder flows that don't yet have actions defined.

### Fixed
- Fixed snapshot restoration not honoring flow running states when the plugin is not already installed. Flow snapshots now capture `sourcePlugin`, enabling placeholder flows to be created during restore. When the plugin later installs, it claims the placeholder and preserves the intended running/stopped state.
- Fixed snapshot import failing with GraphQL parse errors when snapshots contain special characters in string values (e.g., action parameter schemas with complex descriptions).
- Fixed invalid flows never becoming valid through periodic revalidation.
- Fixed plugin image changes being ignored when plugin is in INSTALLING state. Changing the image now correctly triggers a redeploy.
- Fixed plugin registration not finding pending entries when user changes the image before registration completes.
- Fixed plugin staying in INSTALLED state while switching versions. Plugin now stays in INSTALLING until the correct image is running.
- Fixed old plugin container continuing to run after user changes the desired image version. Registration from outdated containers is now rejected.
- Fixed plugin reconciliation blocking all plugins when one plugin has a slow install. Plugins now install in parallel.

### Removed
-

### Deprecated
-

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
- Snapshots created before this version may lose flow running state for plugins that need to install. Please create a new snapshot after upgrading.
