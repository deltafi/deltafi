# Changes on branch `external-storage`
Document any changes on this branch here.
### Added
- Added a `deltafi.storage.url` setting to the `values.yaml` to allow pointing to an external content storage system
- Added a `deltafi.storage.snowball.enabled` setting to the `values.yaml` to allow using snowball uploads to external content storage systems when they support it. By default, this is set to the value of `enabled.minio`

### Changed
- Changed the helm chart to skip the `deltafi-fast-delete` deployment when running with external content storage
- Changed the `Kubernetes Resource Check` configuration to account for disabled services
- Changed the status checks to skip checking content storage when running with external content storage
- Changed content related system properties to be disabled when running with external content storage 

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
- When using external content storage the operator is responsible for the bucket creation and life cycle management. DeltaFi does not attempt to create the bucket or set the age-off when using external content storage.
