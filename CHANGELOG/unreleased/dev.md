# Changes on branch `dev`
Document any changes on this branch here.
### Added
- Docker compose mode introduced as a beta proof of concept
  - From the `compose` directory execute `./compose start`
  - To use the CLI you must unlink the cluster command (execute `deltafi-cli/install.sh`) and add 
    `export DELTAFI_MODE=STANDALONE` to `deltafi-cli/config`
- `appsByNode` endpoint added to core to get docker-based apps-by-node manifest
- `app/version` endpoint added to core to get docker-base version list
- `DockerDeployerService` added to manage plugin installs in compose
- `DockerAppInfoService` added to support `appsByNode` and `app/version` endpoints

### Changed
- Monitored status checks for k8s will only run in CLUSTER mode
- CLI updated to accommodate standalone compose mode

### Fixed
- sse endpoint X-Accel-Buffering turned off to fix stand alone sse streaming

