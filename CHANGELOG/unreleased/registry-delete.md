# Changes on branch `registry-delete`
Document any changes on this branch here.
### Added
- New registry APIs added:
  - `api/v1/registry/add/path/to/registry:tag` - allows direct pull of any publicly accessable docker image into the local registry
```
curl -X POST http://local.deltafi.org/api/v1/registry/add/deltafi/deltafi-stix:1.0.0
```
  - `api/v1/registry/delete/path/to/registry:tag` - allows deletion of local registry image
```
curl -X DELETE http://local.deltafi.org/api/v1/registry/delete/deltafi/deltafi-stix:1.0.0
```
  - `api/v1/registry/list` - generates a json summary of all local registry repositories and their tags

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
