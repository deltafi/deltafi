# Changes on branch `dgs-12`
Document any changes on this branch here.
### Added
- 

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
- DGS framework: 11.1.0 → 12.0.1
- graphql-dgs-jackson2: added as `api` in `deltafi-common-spring` (opt-in module that restores Jackson 2 auto-configuration; DGS 12 makes Jackson 3 the default)
- `dgs.graphql.preferred-json-mapper=jackson2` added to `deltafi-core/application.yaml` so DGS uses the Jackson 2 mapper while the rest of the codebase remains on Jackson 2
