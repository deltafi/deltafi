# Changes on branch `jackson-up`
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
- Migrated the platform from Jackson 2 (`com.fasterxml.jackson`) to Jackson 3 (`tools.jackson`) across all modules, including the DGS GraphQL client, Feign clients, and the YAML/XML/CSV dataformat modules. The `jacksonVersion` pin was dropped in favor of the versions managed by Spring Boot.

### Upgrade and Migration
- Upgraded JSON schema generator to 5.0.0.
- Upgraded from Jackson 2 to Jackson 3 (aligned with the Spring Boot BOM)
- Plugins built against the action kit now resolve Jackson via the `tools.jackson` coordinates rather than `com.fasterxml.jackson`; plugins that reference Jackson types directly will need to update their imports when rebuilding against this release.
