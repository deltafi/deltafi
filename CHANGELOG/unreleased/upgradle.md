# Changes on branch `upgradle`
Document any changes on this branch here.
### Added
- 

### Changed
- New `org.deltafi.test-summary` precompiled-script plugin published from `gradle-plugin`. Downstream DeltaFi plugins that apply `org.deltafi.plugin-convention` now get a pretty boxed summary of test results across subprojects automatically.
- New `org.deltafi.git-version` precompiled-script plugin published from `gradle-plugin`. Derives the project version from `git describe --tags` with no external plugin dependency.

### Fixed
-

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Replaced `Project.exec(Closure)` (deprecated for Gradle 9.0) with an injected `ExecOperations` helper in `deltafi-python/build.gradle`.
- Updated gradle scripts for all Gradle 9.0 deprecations

### Upgrade and Migration
- Bumped Gradle from 8.5 to 8.14.5
