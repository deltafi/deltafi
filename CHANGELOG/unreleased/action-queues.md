# Changes on branch `action-queues`
Document any changes on this branch here.
### Added
- Queue Metrics page: Per-flow breakdown of warm and cold queue metrics with flow type, action name, and plugin tooltips
- Queue Metrics page: "Oldest" timestamp column showing how long items have been queued (for both warm and cold queues)
- Queue Metrics page: Roll-up by action class toggle to aggregate metrics across flows
- Queue Metrics page: Running Tasks panel showing currently executing actions with DID links, duration, and worker info
- REST API: `/api/v2/metrics/queues/detailed` endpoint for per-flow queue metrics
- REST API: `/api/v2/metrics/queues/action-plugins` endpoint for action class to plugin mapping
- REST API: `/api/v2/metrics/queues/running` endpoint for currently executing tasks
- Database: `cold_queue_entries` table with trigger-maintained per-item tracking for accurate oldest timestamps
- Add oldest in flight DeltaFile age to Dashboard and Leader Dashboard

### Changed
-

### Fixed
- Fixed race condition in HeartbeatService where action execution could become null between filter and use, causing running tasks to not be reported (Java plugins must be rebuilt to get this fix)

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
