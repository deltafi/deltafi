# Changes on branch `bad-palantir-bad-bad-bad`
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
- Replaced the `com.palantir.docker` gradle plugin with an internal `org.deltafi.docker` plugin that preserves the same task layout (`dockerClean`, `dockerPrepare`, `docker`, `dockerTag`, `dockerTag<Name>`, `dockerfileZip`) and DSL (`docker { name; dockerfile; tag(name, image); copySpec; files(...) }`). Applied transparently via `docker-conventions` (internal modules) and `org.deltafi.plugin-convention` (external plugin authors); no consumer build.gradle changes required.

### Upgrade and Migration
- 
