# Changes on branch `487-kind-sync`
Document any changes on this branch here.
### Added
- Gradle: `install` task for KinD will build docker images, install them in the KinD cluster, and perform a deltafi up to sync orchestration
- Gradle: `install` task for Compose will build docker images and perform a deltafi up to sync orchestration
- Gradle: `pushToKind` task added to docker builds in KinD mode
- Gradle: `syncKindPods` task added to docker builds in KinD mode
- Gradle: `syncComposeServices` task added to docker builds in Compose mode
- TUI: `deltafi up` for KinD now performs an image sync to detect rebuilt docker tags in development.  This mimics the behavior of `deltafi up` for compose

### Changed
- KinD image managment no longer uses localhost:5000 images
- KinD no longer needs sidecar registry containers
- KinD image caching consolidated and simplified
- Gradle: `configureKindCluster` task renamed to `startKindCluster`

### Fixed
- 

### Removed
- Gradle: `dockerPushLocal` task removed

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- 
