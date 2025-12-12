# Changes on branch `env-var-password-fields`
Document any changes on this branch here.
### Added
- EnvVar parameter type for secure handling of secrets via environment variables
- EnvVarRenderer UI component with warning message and UPPER_SNAKE_CASE validation
- Site Configuration documentation covering site/values.yaml, site/compose.yaml, and site/templates/

### Changed
- SftpIngress now supports passwordEnvVar field for secure password handling (password field deprecated but still functional)

### Fixed
-

### Removed
-

### Deprecated
- SftpIngress.password field (use passwordEnvVar instead)

### Security
- Secrets referenced via EnvVar are never stored in DeltaFi configuration or transmitted through Valkey message queues

### Tech-Debt/Refactor
-

### Upgrade and Migration
- Existing SFTP configurations using the password field will continue to work (backward compatible)
- Note that the new EnvVar type is only available to Java actions at this time
