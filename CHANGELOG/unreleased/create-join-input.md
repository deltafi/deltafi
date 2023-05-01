# Changes on branch `create-join-input`
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
- Refactor JoinAction interfaces to receive a List[JoinInput] describing the content and metadata for the DeltaFiles to be JOINED.  Split JoinResult into JoinResult, which acts like a LoadResult with domains, and JoinReinjectResult, which reinjects the JOINED file onto another ingress or transform flow. 

### Upgrade and Migration
- 
