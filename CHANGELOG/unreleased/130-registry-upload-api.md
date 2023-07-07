# Changes on branch `130-registry-upload-api`
Document any changes on this branch here.
### Added
- `api/v1/registry/catalog` endpoint to list repositories in the internal registry
```
curl http://local.deltafi.org/api/v1/registry/catalog
```
- `api/v1/registry/upload` to upload a docker-archive tar file to the internal registry
```bash
# example
curl -X POST --data-binary @passthrough.tar http://local.deltafi.org/api/v1/registry/upload -H "name: deltafi/passthrough:1.0" -H "Content-Type: application/octet-stream"
```
- Three new permissions added to auth:
  - RegistryView
  - RegistryUpload
  - RegistryDelete
- KinD: Registry enabled by default
- KinD: Registry UI (http://registry.local.deltafi.org) enabled by default

### Changed
- Enabled registry deletion in internal registry by default

