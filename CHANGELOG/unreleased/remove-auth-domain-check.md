# Changes on branch `remove-auth-domain-check`
Document any changes on this branch here.
### Added
-

### Changed
- Simplified authorization to use path-based checks only (removed subdomain-based authorization)

### Fixed
- DeltaFi UI will render regardless of the subdomain that is used

### Removed
- Removed subdomain ingresses for `metrics`, `ingress`, `victoriametrics`, and `minio` (K8s)
- Removed subdomain routing from nginx configuration (Compose)

### Deprecated
-

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
- Subdomain URLs (e.g., `metrics.local.deltafi`, `minio.local.deltafi`) will no longer work
  - Use the main domain with appropriate paths instead
