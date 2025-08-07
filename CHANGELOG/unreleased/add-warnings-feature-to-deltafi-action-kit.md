# Changes on branch `add-warnings-feature-to-deltafi-action-kit`
Document any changes on this branch here.
### Added
- Actions may now log messages which are saved into the DeltaFile and available for view on the TUI and UI (Raw DeltaFile)
  - Log messages with a severity of `WARNING` are flagged and searchable by the UI/TUI
  - An `ErrorResult` automatically generates a log message with a severity of `ERROR`
  - Limited to the Java action kit initially
- A user may now add a note to a DeltaFile. This is accomplished using a log message with a severity of `USER`, and the new GraphQL endpoint `userNote`. This new endpoint requires the new permission `DeltaFileUserNote`
  - Adding a user note is supported through GraphQL initially
  - Searching for DeltaFiles with a user note is supported through the TUI initially
- New core action, `Warning` allows for logging of messages with the `WARNING` severity

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
- New user permission, `DeltaFileUserNote`
- Added migration to add new fields  to `delta_files` table: `warnings`, `user_notes`, and `messages`
