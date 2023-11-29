# Changes on branch `improved-node-metrics`
Document any changes on this branch here.
### Added
- New `namespace` and `node` tags for `gauge.app.memory` and `gauge.app.cpu` metrics
- New metric: `gauge.node.cpu.iowait` to track iowait pressure on nodes
- New dashboard: `DeltaFi > System Performance`

### Changed
- 

### Fixed
- 

### Removed
- System Overview dashboard: Removed app CPU/RAM charts.  These charts were moved to the `System Performance` dashboard

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Batched metrics in nodemonitor and clustermonitor to improve graphite performance

### Upgrade and Migration
- 
