# Changes on branch `upgradle-5`
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
- Gradle 10 deprecations
  - Switched the `com.adarshr.test-logger` configuration block in the root `build.gradle` to explicit `propName = value` assignment syntax. The space-separated Groovy DSL form is deprecated in Gradle 9.5+ for removal in Gradle 10.
  - Replaced the manual `sourcesJar`/`javadocJar` `Jar` tasks and the `artifacts { archives ... }` block in the root `build.gradle` Maven publication setup with the `java { withSourcesJar(); withJavadocJar() }` shortcut. The `archives` configuration is deprecated for artifact declaration in Gradle 9.5+ and removed in Gradle 10; the `java` plugin's shortcuts register the artifacts on `components.java`, so the Maven publication continues to include sources and javadoc jars.
  - Captured `project.name` at configuration time inside the `test-summary-conventions` precompiled-script plugin so the `afterSuite` test listener no longer dereferences `testTask.project` at execution time. Execution-time `Task.project` access is deprecated in Gradle 9.5+ for removal in Gradle 10 and is incompatible with the configuration cache.

### Upgrade and Migration
- 
