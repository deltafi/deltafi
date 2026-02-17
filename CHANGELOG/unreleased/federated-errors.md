# Changes on branch `federated-errors`
Document any changes on this branch here.
### Added
- Fleet Errors page: view and manage errors across all fleet members from the leader, with bulk acknowledge, resume, replay, and replay+acknowledge operations
  - Fleet Errors supports filtering by member, tags, data source, error message, acknowledgement status, and time range
  - Fleet Errors displays error summaries by member, by flow, and by message with aggregated counts
  - Parallel action execution on up to 5 fleet members simultaneously
- REST API: Added federated error endpoints under `/api/v2/leader/errors/`:
  - `POST /search` - Search errors across fleet members
  - `POST /acknowledge` - Acknowledge errors across fleet members
  - `POST /resume` - Resume errors across fleet members
  - `POST /replay` - Replay errors across fleet members
  - `POST /replay-and-acknowledge` - Replay and acknowledge errors across fleet members
  - `POST /summary/by-data-source` - Get error summary by data source
  - `POST /summary/by-message` - Get error summary by error message
- GraphQL: Added `modifiedAfter` and `modifiedBefore` parameters to by-flow mutations for time-based filtering:
  - `resumeByFlow`
  - `acknowledgeByFlow`
  - `replayErrorsByFlow`
  - `replayAndAcknowledgeErrorsByFlow`
- GraphQL: Added `errorCause` parameter to `ErrorSummaryFilter` for filtering error summaries by error message

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
