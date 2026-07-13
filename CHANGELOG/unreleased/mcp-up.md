# Changes on branch `mcp-up`
Document any changes on this branch here.
### Added
- Expanded MCP coverage for system properties, snapshots, status reporting, data ingress, integration tests, auto-resume policies, delete policies, events, DeltaFile annotations, and system plan import/export.
- Added MCP tools to export system flow plans and retrieve an individual flow's plan, both producing payloads that feed straight back into the import and load tools.
- Added MCP tools to generate plugin actions and suppliers and to preview or apply plugin scaffolding updates.

### Changed
- MCP tools now provide stricter input definitions and clearer validation errors for structured data, filters, and paging options.
- Plugin generation now initializes new plugins as standalone Git repositories while preserving the repository structure of existing plugins.

### Fixed
- Fixed MCP data ingress for empty inline content and metadata values.
- TUI: Fixed plugin installation tracking for images that omit an explicit tag.
- TUI: Fixed policy replacement operations so an explicitly empty set can clear existing policies.

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
