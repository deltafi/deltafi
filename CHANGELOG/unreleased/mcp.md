# Changes on branch `mcp`
Document any changes on this branch here.
### Added
- MCP (Model Context Protocol) server integrated into TUI - run `deltafi mcp` to start
- 48 MCP tools across 8 tag groups for AI agent integration:
  - `debug` (6 tools): DeltaFile queries, search, statistics, error summaries
  - `operations` (2 tools): Resume and acknowledge errors
  - `flows` (12 tools): List/get/start/stop flows, topics, action descriptors, load and validate flows
  - `ingest` (5 tools): Data source management, timed source scheduling
  - `plugins` (8 tools): Plugin listing, details, installation, uninstallation
  - `admin` (7 tools): System properties, snapshots, delete policies, SSL info, integration tests
  - `metrics` (5 tools): System status, queue stats, node metrics, versions
  - `generate` (3 tools): Plugin and action scaffolding for Java and Python
- `.mcp.json` configuration file for Claude Code integration
- GraphQL validation queries for all data source types (REST, timed, on-error)
- Documentation for MCP server usage and AI integration

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
