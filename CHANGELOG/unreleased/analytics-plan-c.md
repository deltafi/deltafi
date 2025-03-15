# Changes on branch `analytics-plan-c`
Document any changes on this branch here.
### Added
- Add Filter Analysis grafana dashboard

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
- Overhaul analytics to improve performance 
- Replace Data Source by Annotation and Pivot by Annotation dashboards with Dataflow Analytics grafana dashboard

### Upgrade and Migration
- Old analytic metrics data will be deleted
- Custom grafana dashboards built with the old data model will no longer work
- Set values for configutation properties allowedAnalyticsAnnotations and analyticsGroupName. See analytics documentation for more details
