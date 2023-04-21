# Changes on branch `simple-transform-flow`
Document any changes on this branch here.
### Added
- DeltaFi now supports two processing modes:
  - NORMALIZATION - the classic processing mode, consisting of Ingress, Enrich, and Egress flows 
  - TRANSFORMATION - a new mode consisting of linear Transform flows. A transform flow has a series of TransformActions followed by an EgressAction.  If the final TransformAction in the chain produces multiple pieces of content, they will all be egressed using child DeltaFiles.  For example:
```json
{
  "name": "simple-transform",
  "type": "TRANSFORM",
  "description": "A simple transform flow that processes data and sends it out using REST",
  "transformActions": [
    {
      "name": "FirstTransformAction",
      "type": "org.deltafi.example.action.FirstTransformAction"
    },
    {
      "name": "SecondTransformAction",
      "type": "org.deltafi.example.action.SecondTransformAction"
    }
  ],
  "egressAction": {
    "name": "SimpleEgressAction",
    "type": "org.deltafi.core.action.RestPostEgressAction",
    "parameters": {
      "egressFlow": "simpleTransformEgressFlow",
      "metadataKey": "deltafiMetadata",
      "url": "${egressUrl}"
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
