# Changes on branch `minio-replacement`
Document any changes on this branch here.
### Added
- Added `enable.local_object_storage` configuration option (replaces `enable.minio`)

### Changed
- Replace MinIO with s3proxy for S3-compatible object storage (MinIO is no longer open source)

### Fixed
-

### Removed
- Removed `deltafi minio` CLI commands (s3proxy does not include MinIO client)
- Removed SNOWBALL bulk upload support (MinIO-specific feature not supported by s3proxy)

### Deprecated
- `enable.minio` configuration option (use `enable.local_object_storage` instead)
- `SNOWBALL_ENABLED` environment variable (retained only for backward compatibility with older plugins)

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
- Migration of Minio to s3proxy
  - No data migration required - existing content storage works as-is
  - Existing plugins will continue to work via `deltafi-minio` service alias and `MINIO_*` environment variables
- New container image: deltafi/s3proxy:2.9.0-0
