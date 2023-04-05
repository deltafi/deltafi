# Changes on branch `477-local-helm-subcharts`
Document any changes on this branch here.
### Added
- 

### Changed
- Helm charts are now local charts rather than helm dependencies

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
- Before upgrading, run `utils/helminator.sh` to clean up helm dependencies
- Upgraded helm charts for:
  - mongodb
  - grafana
  - promtail
  - graphite
- Upgraded Grafana to 9.4.7
- Upgraded Promtail to 2.8.0
- Upgraded Loki to 2.8.0
- Upgraded Graphite to 1.1.10-4
- Upgraded Redis to 7.0.8
- KinD: Upgraded metrics-server to v0.6.3
