# Changes on branch `89-read-receipts`
Document any changes on this branch here.
### Added
- Added a new field, `expectedAnnotations`, in the transform and egress flows containing a set of annotations that are expected when a `DeltaFile` goes through the flow
- Added new mutations to set the expected annotations in transform and egress flows
   ```graphql
   # Example mutation setting the expected annotations on a transform flow named passthrough
   mutation {
     setTransformFlowExpectedAnnotations(flowName:"passthrough", expectedAnnotations:["readBy", "readAt"])
   }

   # Example mutation setting the expected annotations on an egress flow named passthrough
   mutation {
     setEgressFlowExpectedAnnotations(flowName:"passthrough", expectedAnnotations:["readBy", "readAt"])
   }
   ```

### Changed
- Delete policies will not remove a `DeltaFile` until all expected annotations have been set

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
