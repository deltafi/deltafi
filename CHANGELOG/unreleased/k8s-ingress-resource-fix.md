# Changes on branch `k8s-ingress-resource-fix`
Document any changes on this branch here.
### Added
- 

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
- Simplified the DeltaFi UI ingress to plain `pathType: Prefix` rules and removed the `nginx.ingress.kubernetes.io/rewrite-target` annotation. The regex capture-group paths (`/(.*)`, `/(visualization.*)`, ...) combined with an
  identity rewrite (`/$1`) were a no-op, so routing is unchanged.

### Upgrade and Migration
- 
