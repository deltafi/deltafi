# Changes on branch `convert-worker-back-to-deployment`
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
- Change core-worker from a statefulSet to a deployment in k8s

### Upgrade and Migration
- Kubernetes upgrade to this version will require removing the core worker statefulset prior to upgrade:
```
kubectl delete statefulset deltafi-core-worker
```
