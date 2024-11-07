# Changes on branch `transform-child-id`
Document any changes on this branch here.
### Added
- 

### Changed
- Create the child DeltaFile `did` from the action kits so the child `did` can be used within the child result

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
- TransformResults now collect a new type, `ChildTransformResult`, in both java and python instead of collecting objects of type TransformResult. 

Java Sample:
```java
TransformResults manyResults = new TransformResults(context);
ChildTransformResult result = new ChildTransformResult(context, name);
manyResults.add(result); 
```
Python Sample:
```python
transform_many_result = TransformResults(context)
child = ChildTransformResult(context, name)
transform_many_result.add_result(child)
```