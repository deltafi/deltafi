# Changes on branch `mongo-chart`
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
- 

### Upgrade and Migration
- Helm chart upgrades
  - minio - 5.0.9
  - mongodb - 14.4.0
  - redis - 18.4.0
  - kubernetes-dashboard - 6.0.8
  - promtail - 6.15.2
  - loki - 2.16.0
  - grafana - 7.0.11
  - clickhouse - 4.1.8
- When installing this version, mongodb will be updated and due to a bug in mongodb rolling initialization, the mongodb pod in a kubernetes cluster should be scaled to 0 instances:
```bash
# Prior to upgrade, scale the deltafi-mongodb pod to 0
kubectl scale deploy deltafi-mongodb --replicas=0
# During upgrade, after helm starts, scale the deltafi-mongodb pod to 1
kubectl scale deploy deltafi-mongodb --replicas=1
```
