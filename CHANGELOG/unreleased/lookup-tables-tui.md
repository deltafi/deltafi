# Changes on branch `lookup-tables-tui`
Document any changes on this branch here.
### Added
- Added table lookup service commands to the TUI:
  - deltafi lookup list - lists all lookup tables
  - deltafi lookup schema-import [schema-file] - imports a lookup table schema (creates table structure)
  - deltafi lookup schema-export [name] - exports a lookup table schema (table structure without data)
  - deltafi lookup delete [name] - deletes a lookup table (use --force to skip confirmation)
  - deltafi lookup query [name] - displays rows (use --json, --yaml, --csv, or --plain for output format)
  - deltafi lookup insert [name] [rows-file] - inserts/updates rows from CSV, JSON, or YAML (file or stdin)

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
