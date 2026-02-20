# Changes on branch `k8s-global-env`
Document any changes on this branch here.
### Added
- Added `deltafi.globalEnv` in `site/values.yaml` to inject environment variables into all DeltaFi-built services. Works in both Kubernetes mode (via Helm templates, supports `valueFrom`) and Compose mode (written to `common.env`)

### Changed
- Analytics deployment now includes common environment variables (Helm and Compose)
- Generated `common.env` now includes a header comment and sorted keys

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
