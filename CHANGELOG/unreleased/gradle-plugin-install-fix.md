# Changes on branch `gradle-plugin-install-fix`
Document any changes on this branch here.
### Added
-

### Changed
- Plugin SNAPSHOT builds now use timestamped tags (e.g., `2.44.1-SNAPSHOT-20251217-143022`) to ensure each build gets a unique image tag
- Plugin gradle builds automatically prune old SNAPSHOT images before building (keeps 3 most recent)
- Made plugin jar builds reproducible by excluding build timestamp and normalizing file order

### Fixed
- Fixed issue where `./gradlew install` in plugin projects would not restart the plugin container even when the image changed (now uses unique tags so core naturally detects the change)
- Fixed issue where `./gradlew install` did not respect the overridden docker image name in the Gradle `docker { }` configuration

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
