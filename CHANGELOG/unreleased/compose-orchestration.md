# Changes on branch `compose-orchestration`
Document any changes on this branch here.
### Added
- Compose: deltafi-core-worker can be added via site/values.yaml:
```yaml
---
deltafi:
  core_worker:
    enable: true
    replicas: 1 # Or as many instances as you want
```
- Compose: deltafi-core-actions can be scaled via site/values.yaml:
```yaml
---
deltafi:
  core_actions:
    replicas: 2 # Or as many instances as you want
```
- TUI: HTTP client allows internal DNS mapping to remove the need for changes to /etc/hosts
- TUI: `site/compose.yaml` can be used to override or extend the compose orchestration
- TUI: Compose orchestration creates required data directories and creates the Docker DeltaFi network if it does not exist
- TUI: Compose orchestration automatically detects and configures local user ID, group ID, and docker group ID

### Changed
- Compose: Docker UI now uses `/orchestration` path instead of `orchestration.<fqdn>`
- TUI: Compose orchestration no longer needs to place domains in /etc/hosts
- UI sidebar links to new Docker Dashboard location
- TUI: No dependencies on legacy CLI or compose scripts for operation of compose stack

### Fixed
- 

### Removed
- CLI: Removed `_api_v1` deprecated function

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- Compose docker-web-gui updated to 1.0.2-1
