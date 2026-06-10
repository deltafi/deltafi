# Changes on branch `modify-metadata-regex`
Document any changes on this branch here.
### Added
- Added an ordered operations pipeline to ModifyMetadata. Beyond the existing add/copy/delete, you can now bulk-edit metadata by regex without listing keys:
    - DELETE_MATCHING — delete all keys matching a regex
    - REPLACE_KEY — rename keys by regex (e.g. make every key HTTP-header-safe)
    - REPLACE_VALUE — rewrite values by regex, optionally scoped to matching key
- Added the option to launch the SPeL editor for boolean fields and map values when editing action parameters

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
