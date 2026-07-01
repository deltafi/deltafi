# Changes on branch `plugin-properties-toggle-switch`
Document any changes on this branch here.
### Added
- On the System Properties page, added support for editing masked (sensitive) values via a password field that stays blank on open so the stored secret is never overwritten
- On the System Properties page, added a lock icon for masked variables and a red `*` indicator for required variables

### Changed
- The System Properties page now shows toggle switches for boolean plugin variables, making it consistent with system properties

### Fixed
- On the System Properties page, fixed long property values overflowing the value column by wrapping them
- On the System Properties page, fixed LIST/SET/MAP value chips being invisible on striped rows in light mode by giving them a background and border

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
