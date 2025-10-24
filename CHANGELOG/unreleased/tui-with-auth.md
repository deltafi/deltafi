# Changes on branch `tui-with-auth`
Document any changes on this branch here.
### Added
- Added an option to use a specific DeltaFi API URL per TUI command with the `--api-url` flag (highest precedence)
- Added an option to configure the TUI with an environment variable called `DELTAFI_API_URL` to use a specific DeltaFi API URL instead of detecting it (second in precedence) 
- Added an option to configure the TUI to use a specific DeltaFi API URL with authentication in `.deltafi/config.yaml` (URL here has the lowest precedence). See examples below:
```yaml
context:
  api:
    url: https://local.deltafi.org
  authentication:
    basic:
      username: <USERNAME>
      password: <PASSWORD>
# for cert authentication use the following      
#   cert:
#     keyPath: <path to key file>
#     certPath: <path to cert file>
```

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
- 

### Upgrade and Migration
- 
