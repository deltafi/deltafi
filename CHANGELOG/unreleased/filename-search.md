# Changes on branch `filename-search`
Document any changes on this branch here.
### Added
- Added filename search options to make regex and case-sensitive searches optional. For example: 
  ```graphql
  query {
    deltaFiles(
        filter: {sourceInfo: {filenameFilter: {filename: "stix.*", regex: true, caseSensitive: false}}}
    ) {
      deltaFiles {
        did
        sourceInfo {
          filename
        }
      }
    }
  }
  ```

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
