# Changes on branch `171-filtered-queries`
Document any changes on this branch here.
### Added
- Added a query, `filteredSummaryByFlow`, to get a summary of filtered DeltaFiles grouped by flow
  ```graphql
    query {
      filteredSummaryByFlow {
        offset
        count
        totalCount
        countPerFlow {
          flow
          count
          dids
        }
      }
  }
  ```
- Added a query, `filteredSummaryByMessage`, to get a summary of filtered DeltaFiles grouped by flow and the filter cause message
  ```graphql
    query {
      filteredSummaryByMessage {
        offset
        count
        totalCount
        countPerMessage {
          flow
          message
          count
          dids
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
