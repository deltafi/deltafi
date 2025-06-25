# Changes on branch `on-error-data-source`
Document any changes on this branch here.
### Added
- Added On-Error Data Sources, a new type of data source that automatically triggers when errors occur in other flows
- Added filtering options for On-Error data sources:
  - `errorMessageRegex` - Filter by error message patterns
  - `sourceFilters` - Unified filtering structure with optional fields:
    - `flowType` - Filter by source flow type (REST_DATA_SOURCE, TIMED_DATA_SOURCE, TRANSFORM, DATA_SINK)
    - `flowName` - Filter by specific flow name
    - `actionName` - Filter by specific action name that generated the error
    - `actionClass` - Filter by action class for cross-flow error monitoring
  - `metadataFilters` - Filter by source DeltaFile metadata key-value pairs
  - `annotationFilters` - Filter by source DeltaFile annotation key-value pairs
  - `includeSourceMetadataRegex` - Include matching metadata from source DeltaFile in error DeltaFile
  - `includeSourceAnnotationsRegex` - Include matching annotations from source DeltaFile in error DeltaFile
- Added GraphQL mutations for creating and managing On-Error data sources
- Added documentation explaining On-Error data source configuration and use cases with detailed filtering examples

### Changed
- Child DeltaFiles now inherit the depth of their parent flow (parent depth + 1)
  - The existing `maxDepth` configuration now prevents infinite recursion across spawned children, not just within a single DeltaFile

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
- Database migration adds support for OnError data source configuration fields (negligible migration time)
