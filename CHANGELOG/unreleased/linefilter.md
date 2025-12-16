# Changes on branch `linefilter`

### Added
- `FilterLines` transform action for line-based content filtering and selection. Supports:
  - `headLines` - Keep only the first N lines (after skip)
  - `tailLines` - Keep only the last N lines (after head)
  - `skipLines` - Skip the first N lines (e.g., for header removal)
  - `includePatterns` - List of regex patterns to keep matching lines
  - `excludePatterns` - List of regex patterns to remove matching lines
  - `includeMatchMode` / `excludeMatchMode` - Control pattern matching logic (OR: match any, AND: match all)
  - `keepEmpty` - Keep or remove empty lines (default: true)
  - `lineDelimiter` - Custom line delimiter (default: `\n`)
  - `preserveDelimiter` - Preserve original delimiter or use system line separator
  - Inherits content selection parameters from `ContentSelectingTransformAction`

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
