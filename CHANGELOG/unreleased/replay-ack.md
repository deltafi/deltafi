# Changes on branch `replay-ack`
Document any changes on this branch here.
### Added
- Replay and Acknowledge combined operation: replay errored DeltaFiles and acknowledge the originals in one step, available via GraphQL, UI, and TUI
- TUI: `deltafi errored resume` command to resume errored DeltaFiles from the command line with filtering by data source, flow, error cause, and more
- TUI: `deltafi errored replay` command to replay errored DeltaFiles, with `--acknowledge` flag to also acknowledge originals
- TUI: `deltafi errored acknowledge` command to acknowledge errors with a required reason
- UI: Replay option in viewer allows acknowledgement of the original error if the file is in the errored state

### Changed
- Updated error handling documentation with comprehensive instructions for all recovery methods via UI and TUI
- UI: Viewer menu items reorganized by function
- Consistent use of "play" icon for resume and "redo" icon for replay operations in the UI

### Fixed

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
