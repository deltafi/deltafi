# Changes on branch `go-act-kit-uppy`
Document any changes on this branch here.
### Added
- Go and Python plugins can now opt out of Dockerfile updates during `deltafi plugin generate update` by setting `ARG CUSTOM_DOCKERFILE=true`
- Plugin updates now preserve user-added and user-modified ENV variables in Dockerfiles
- Go action kit supports YAML format for flow variable files (`variables.yaml`), with multi-document support

### Changed
- Go plugin code generator now produces `variables.yaml` instead of `variables.json` for flow variables

### Fixed
- Plugin updates no longer overwrite user-edited flow definition files (`variables.yaml`, `variables.json`, `data-source.json`)

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
