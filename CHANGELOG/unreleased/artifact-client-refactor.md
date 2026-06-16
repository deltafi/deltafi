# Changes on branch `artifact-client-refactor`
Document any changes on this branch here.
### Added
- Added an `ArtifactFactory` that is a framework-agnostic interface for the artifact registry letting plugins declare artifact handles via a fluent builder (an alternative to `@PluginArtifact` injection)
- Added a `TestArtifactFactory` in deltafi-action-kit-test to simplify testing actions that use the `ArtifactFactory`

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
- Refactored the deltafi-action-kit artifact subsystem so the registry client, cache, version listener, and handle primitives are plain Java with no Spring coupling, isolating Spring wiring to the action-kit configuration

### Upgrade and Migration
-
