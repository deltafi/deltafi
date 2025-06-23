# Changes on branch `delta-files-filter-annotation-search-exists`
Document any changes on this branch here.
### Added
- 

### Changed
- When performing a DeltaFiles - Search using annotations, the following options are now supported in the annotation search value (Gitlab-467):
  - If the value is `*`, the query will only check that the key exists (matches any value)
      - E.g., `*` will match `null`, `"""` (empty string), or `abcd`
  - If the value contains `*`, the query will match if the value is LIKE the provided value, treating `*` as a wildcard
    - E.g., `abc*3` will match `abc123`, but not `abcdef`
  - If the value starts with `!`, the query will add a `NOT` to the comparison
    - E.g., `!abc*3` will match `abcdef`, but not `abc123`
  - When an `!` or `*` is not present, the default behaviour remains an exact match
    - E.g., `abc` will only match `abc` 
    
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
