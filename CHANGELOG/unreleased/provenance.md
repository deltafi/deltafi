# Changes on branch `provenance`
Document any changes on this branch here.
### Added
- Provenance tracking for DeltaFile lineage export. When enabled, creates Parquet files recording the journey of each file through the system (data source, transforms, data sink, final state, timestamps, annotations). Designed for environments with dynamic scaling and ephemeral instances.
- New system properties: `provenanceEnabled`, `provenanceAgeOffDays`, `provenanceAnnotationsAllowed`
- New `/provenance` endpoint in deltafi-analytics for receiving provenance records
- Grafana Provenance dashboard with filtering by data source, data sink, filename, and annotation key/value
- Documentation for provenance tracking in `deltafi-docs/docs/advanced/provenance.md`

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
-
