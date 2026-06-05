# Changes on branch `upgradle-4`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Migrated `SftpIngress` from `com.jcraft.jsch.Session.setPassword(String)` (deprecated in jsch 2.28+) to the `setPassword(byte[])` overload using UTF-8 bytes.

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Migrated the `org.deltafi.test-summary` precompiled-script plugin (and its `buildSrc` twin) from `Test.afterSuite(Closure)` to `Test.addTestListener(TestListener)`. The `afterSuite` form is deprecated in Gradle 9.5+ for removal in Gradle 10.

### Upgrade and Migration
- Gradle wrapper: 8.14.5 → 9.5.1 (major). Downstream plugin authors who consume `org.deltafi.plugin-convention` should regenerate their wrappers via `./gradlew wrapper --gradle-version 9.5.1` after bumping the convention plugin's version. 
