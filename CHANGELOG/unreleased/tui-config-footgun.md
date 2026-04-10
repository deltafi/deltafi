# Changes on branch `tui-config-footgun`
Document any changes on this branch here.
### Added
- TUI: `deltafi config --wizard` flag to launch the interactive configuration wizard
- TUI: `deltafi config --migrate-config` flag that moves an existing `~/.deltafi/config.yaml` (and the associated `rc.bash`/`rc.zsh`/`rc.fish`) into the executable-relative `<deltafi-install>/config/` directory and recreates the shell rc file in the new location
- TUI: first-time setup automatically launches the wizard in an interactive shell and writes a default `config.yaml` in non-interactive sessions, so existing scripts continue to work without manual setup
- TUI: stale DeltaFi source lines in `.bashrc`/`.zshrc`/`config.fish` (lines marked `# Required for DeltaFi configuration`) are stripped and replaced when the shell rc is reconfigured

### Changed
- TUI: `deltafi config` needs explicit `--wizard` flag to launch configuration wizard
- TUI: default location of `config.yaml` and the shell `rc.*` files is now `<deltafi-install>/config/` (next to the `deltafi` executable). `~/.deltafi/config.yaml` is still honored when it already exists, so existing installs continue to work unchanged until they opt in via `--migrate-config`. New installs no longer create files under `~/.deltafi`.
- Gradle build (`build.gradle` and `org.deltafi.plugin-convention.gradle`): `loadDeltafiConfig()` now resolves `config.yaml` via `$DELTAFI_CONFIG_PATH` → `~/.deltafi/config.yaml` → `<deltafi-install>/config/config.yaml`, matching the TUI's resolution order
- Generated Python plugin Makefile detects the DeltaFi `deploymentMode` from either the legacy or executable-relative `config.yaml`

### Fixed
- TUI overwrite configuration when running `deltafi config` from a non-interactive session

### Removed
-

### Deprecated
- `~/.deltafi/` as the default location for the TUI configuration. Existing files there are still honored, but the location will be removed in a future release. Use `deltafi config --migrate-config` to move an existing config into the new location.

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
- Existing installs continue to work without action. To migrate an existing `~/.deltafi/config.yaml` into the new executable-relative location, run `deltafi config --migrate-config`. The migration copies the config, removes the legacy file, recreates the shell rc file in the new location, and rewrites your shell rc (`.bashrc`/`.zshrc`/`config.fish`) to source it from the new path.
