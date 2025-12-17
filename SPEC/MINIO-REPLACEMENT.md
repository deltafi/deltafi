# MinIO Replacement Specification

## Background

MinIO has announced it will no longer maintain its open source project. This document analyzes how MinIO is used in DeltaFi, evaluates alternatives, and documents the s3proxy implementation.

## Current MinIO Usage in DeltaFi

### Core Architecture

DeltaFi uses MinIO as object storage implementing an S3 interface. Content (file data) is stored in MinIO while metadata is stored in PostgreSQL. The storage is organized into "segments" that reference portions of stored objects, enabling efficient handling of large files and zero-copy operations.

**Critical Implementation Detail:** DeltaFi already bypasses MinIO for deletes due to performance constraints. The fast-delete daemon directly removes files from the underlying filesystem. This means the actual storage is just local filesystem storage with an S3 API layer on top for reads/writes.

### Key Interfaces

#### ObjectStorageService (Java Interface)
Location: `deltafi-common/src/main/java/org/deltafi/common/storage/s3/ObjectStorageService.java`

```java
public interface ObjectStorageService {
    // Get object with offset/size support (critical for streaming partial reads)
    InputStream getObject(ObjectReference objectReference) throws ObjectStorageException;

    // Store object from stream
    ObjectReference putObject(ObjectReference objectReference, InputStream inputStream) throws ObjectStorageException;

    // Batch upload
    void putObjects(String bucket, Map<ObjectReference, InputStream> objectsToSave) throws ObjectStorageException;

    // Delete operations (NOTE: bypassed in production - fast-delete daemon is used instead)
    void removeObject(ObjectReference objectReference);
    boolean removeObjects(String bucket, List<String> objectNames);
}
```

#### ObjectReference
Location: `deltafi-common/src/main/java/org/deltafi/common/storage/s3/ObjectReference.java`

```java
public class ObjectReference {
    private String bucket;
    private String name;
    private long offset;  // Critical: byte offset for partial reads
    private long size;    // Critical: byte count for partial reads
}
```

### Critical Feature: Offset/Size Based Reading

The most critical feature is the ability to read a portion of a file using offset and size:

```java
// MinioObjectStorageService.getObject()
minioClient.getObject(GetObjectArgs.builder()
    .bucket(objectReference.getBucket())
    .object(objectReference.getName())
    .offset(objectReference.getOffset())  // S3 Range header
    .length(objectReference.getSize())    // S3 Range header
    .build());
```

This uses S3's HTTP Range header feature to stream only the requested bytes. This enables:
- Subcontent extraction without full file download
- Efficient handling of large files
- Zero-copy segment stitching (multiple segments can reference different ranges of the same stored object)

### Segment System

Location: `deltafi-common/src/main/java/org/deltafi/common/content/Segment.java`

Content is tracked via segments that reference:
- `uuid`: Unique identifier for the stored object
- `did`: DeltaFile identifier (parent)
- `offset`: Byte offset within the stored object
- `size`: Number of bytes

Object path format: `{did[0:3]}/{did}/{uuid}` (e.g., `abc/abcdef-1234.../segment-uuid`)

This allows:
- Creating "subcontent" views of existing content without copying data
- Appending content by adding segment references
- Efficiently tracking overlapping ranges

### Fast-Delete System (Critical)

Location: `deltafi-nodemonitor/deleteit.sh`

**The MinIO API is NOT used for deletes.** Deletes are handled by a daemonset that directly removes files from the filesystem:

```bash
DATA_DIR="/data/deltafi/minio"
path="${DATA_DIR}/${bucket}/${prefix}/${did}"
rm -rf "$path"
```

This was implemented because MinIO's delete API was too slow for DeltaFi's high-volume deletion requirements.

The fast-delete system:
1. Core writes pending deletes to `pending_deletes` table in PostgreSQL
2. DaemonSet (one per node) queries for pending deletes assigned to its node
3. Uses `rm -rf` to delete the content directory directly
4. Removes the pending delete record

This means:
- **MinIO is only used for reads and writes, not deletes**
- **The actual storage is just local filesystem**
- **Any replacement must either allow direct filesystem access OR be fast enough for high-volume deletes**

### Bucket Lifecycle (Failsafe Expiration)

MinIO supported bucket lifecycle policies that automatically expired old objects. This served as a failsafe in case the delete policies failed. With s3proxy, this feature is not available. The `StorageConfigurationService` now gracefully handles this:

```java
private void trySetExpiration() {
    try {
        updateAgeOffIfChanged();
    } catch (Exception e) {
        log.warn("Bucket lifecycle management not supported - using DeltaFi delete policies");
    }
}
```

### Python SDK Usage

Location: `deltafi-python/src/deltafi/storage.py`

```python
class ContentService:
    def get_bytes(self, segments: List[Segment]):
        return b"".join([self.minio_client.get_object(
            self.bucket_name,
            segment.id(),
            segment.offset,  # Range start
            segment.size     # Range length
        ).read() for segment in segments])

    def put_bytes(self, did, bytes_data):
        segment = Segment(uuid=str(uuid.uuid4()), offset=0, size=len(bytes_data), did=did)
        self.minio_client.put_object(self.bucket_name, segment.id(), io.BytesIO(bytes_data), len(bytes_data))
        return segment
```

### Deployment

**Data Path:** `/data/deltafi/minio/{bucket}/{prefix}/{did}/`

**Docker Compose:**
- Service: `deltafi-s3proxy`
- Data volume: `${DATA_DIR}/minio:/data`
- Profile: `localStorage`
- Port: 9000 (API)

**Kubernetes:**
- Deployment: `deltafi-s3proxy`
- Service: `deltafi-s3proxy`
- DaemonSet: `deltafi-node-fastdelete` for direct filesystem deletes

## Requirements for Replacement

### Must Have

1. **Streaming reads with offset/size** - read arbitrary byte ranges from files (S3 Range header)
2. **Streaming writes** - handle large files without loading into memory
3. **Direct filesystem access OR fast API deletes** - the fast-delete daemon needs to work
4. **S3-compatible API** - existing Java/Python clients must work unchanged
5. **Open Source License** - Apache 2.0 or similar permissive license

### Performance Requirements

1. **High-throughput reads** - actions read content frequently
2. **High-throughput writes** - ingress writes content continuously
3. **High-volume deletes** - retention policies delete large amounts of content
4. **Concurrent access** - multiple actions reading/writing simultaneously

## Alternative Candidates

### 1. s3proxy with Filesystem Backend (Selected)

[s3proxy](https://github.com/gaul/s3proxy) translates S3 API calls to various storage backends.

**Pros:**
- S3-compatible API with Range header support (verified in code)
- Uses local filesystem - fast-delete continues working unchanged
- Lightweight (~50MB container)
- Apache 2.0 license
- Can swap to real S3 or other backends by changing configuration

**Cons:**
- Deprecated `filesystem` backend required (see Known Issues)
- No bucket lifecycle support
- Smaller community than MinIO

### 2. Garage

[Garage](https://garagehq.deuxfleurs.fr/) is a lightweight S3-compatible distributed storage system.

**Pros:**
- S3-compatible API with Range header support
- Designed for self-hosting
- Written in Rust, lightweight
- Apache 2.0 license

**Cons:**
- Different storage layout - cannot do direct filesystem deletes
- Would need to test delete API performance
- Smaller community

### 3. SeaweedFS

[SeaweedFS](https://github.com/seaweedfs/seaweedfs) is a distributed storage system with S3 gateway.

**Pros:**
- S3 gateway with Range header support
- Designed for high-volume operations
- Apache 2.0 license

**Cons:**
- More complex architecture (master + volume + filer + s3)
- Different storage model - cannot do direct filesystem deletes
- Would need to test delete performance

### 4. Custom File Server

Write a minimal HTTP server that implements just the S3 operations we need.

**Pros:**
- Exactly what we need, nothing more
- Full control
- No external dependencies

**Cons:**
- Must implement multipart upload for large files
- Must implement S3 auth
- Ongoing maintenance burden
- Loses ability to swap to real S3

## s3proxy Implementation

### Docker Compose Configuration

```yaml
deltafi-s3proxy:
  image: andrewgaul/s3proxy:sha-59fb92e
  container_name: deltafi-s3proxy
  expose:
    - "9000"
  environment:
    S3PROXY_AUTHORIZATION: aws-v2-or-v4
    S3PROXY_ENDPOINT: "http://0.0.0.0:9000"
    JCLOUDS_PROVIDER: filesystem
    JCLOUDS_FILESYSTEM_BASEDIR: /data
  env_file:
    - ${SECRETS_DIR}/minio.env
  volumes:
    - ${DATA_DIR}/minio:/data
```

### Credentials

s3proxy reads `S3PROXY_IDENTITY` and `S3PROXY_CREDENTIAL` from the environment. These are added to `minio.env`:

```bash
MINIO_ACCESSKEY=deltafi
MINIO_SECRETKEY=<password>
S3PROXY_IDENTITY=deltafi
S3PROXY_CREDENTIAL=<password>
```

The existing `MINIO_*` variables are retained for backward compatibility with the Java/Python clients.

### Storage Layout

Objects are stored directly as files, matching MinIO's layout:

```
${DATA_DIR}/minio/
└── storage/           # bucket name
    └── abc/           # first 3 chars of DID
        └── {did}/     # DeltaFile ID
            └── {uuid} # segment UUID (the actual file)
```

**No data migration required** - existing data works as-is.

## Known Issues

### Deprecated `filesystem` Backend

s3proxy logs this warning:
```
WARNING: filesystem storage backend deprecated -- please use filesystem-nio2 instead
```

The recommended `filesystem-nio2` backend uses Java NIO2's extended attributes (xattr) to store S3 metadata. However, Docker for Mac does not support xattr on mounted volumes, causing read failures:

```
java.nio.file.FileSystemException: Unable to get list of extended attributes: Operation not supported
```

The deprecated `filesystem` backend doesn't use xattr and works on all platforms.

**Resolution:** The s3proxy maintainer [acknowledges this limitation](https://github.com/gaul/s3proxy/issues/784) and recommends using the deprecated backend as a workaround. There is no configuration option to disable xattr in `filesystem-nio2`. Since DeltaFi doesn't use S3 object metadata (which is what xattr stores), the `filesystem` backend meets our needs. We will continue using it until a better solution emerges.

### TTL/Expiration Failsafe

MinIO's bucket lifecycle provided a failsafe expiration mechanism that automatically expired old objects. With s3proxy, this feature is not available.

**Decision:** No additional failsafe mechanism is needed. DeltaFi core already sweeps for DeltaFiles older than `ageOffDays` and sends delete instructions to fast-delete. The bucket lifecycle was a redundant safety net. If PostgreSQL is down (which would prevent the core sweep), the entire system is non-functional anyway, so content expiration is not the primary concern.

## Kubernetes Implementation

The Kubernetes implementation replaces the MinIO subchart with simple deployment and service templates.

### Changes Made

1. **Removed MinIO subchart dependency** from `Chart.yaml`
2. **Deleted MinIO subchart** from `charts/minio/`
3. **Created `deployment-s3proxy.yaml`**:
   - Single-replica deployment (like compose)
   - Uses same `minio-keys` secret for credentials
   - Mounts existing `deltafi-minio` PVC
   - Uses `filesystem` backend (same as compose)
   - Includes TCP socket liveness/readiness probes on port 9000
4. **Created `service-s3proxy.yaml`**:
   - Named `deltafi-s3proxy`
   - Exposes port 9000 (same as MinIO)
5. **Updated `values.yaml`**:
   - Replaced `minio:` section with `s3proxy:` configuration
   - Updated `deltafi.storage.url` to `http://deltafi-s3proxy:9000`
   - Added `enable.local_object_storage` toggle (replaces deprecated `enable.minio`)
6. **Removed `deltafi minio` CLI commands**:
   - s3proxy doesn't include the MinIO client (`mc`)
   - Removed `cmd/minio.go` and related orchestrator methods
   - Users can access the filesystem directly if needed for debugging
7. **Updated all references** from `deltafi-minio` to `deltafi-s3proxy`:
   - Docker Compose service name
   - Kubernetes deployment/service/ingress names
   - MINIO_URL environment variable
   - nginx proxy configuration

### PVC Compatibility

The PVC remains named `deltafi-minio` for backward compatibility with existing deployments. This avoids requiring data migration or PVC recreation. The data directory path (`${DATA_DIR}/minio`) also remains unchanged. No data migration required - the filesystem layout is identical.

### Plugin Backward Compatibility

Existing plugins have `MINIO_URL=http://deltafi-minio:9000` baked into their deployments (set by core at install time). To avoid requiring plugin reinstalls, a service alias `deltafi-minio` is created that routes to the s3proxy pods. This allows existing plugins to continue working without changes.

New plugin installs will get `MINIO_URL=http://deltafi-s3proxy:9000`.

## Remaining Work

### Performance Testing

Need to benchmark s3proxy vs MinIO for:

1. **Read throughput** - single and concurrent reads
2. **Read latency** - especially for Range requests
3. **Write throughput** - single and concurrent writes
4. **Large file handling** - multipart uploads
