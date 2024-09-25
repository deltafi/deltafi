# Changes on branch `pem-based-ssl-context`
Document any changes on this branch here.
### Added
- Added a new command, `deltafi configure-plugin-ssl`, for setting up keys and certs for plugins
- Add documentation for configuring SSL in plugins

### Changed
- 

### Fixed
- 

### Removed
- Removed `deltafi.ssl.*` fields from the `values.yaml` leaving just the secret field. Core and plugins are now setup to look for key and cert files in a fixed location (/certs)

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Use pem files for setting up SSLContexts instead of JKS or p12 formats

### Upgrade and Migration
- 
