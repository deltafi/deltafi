# DeltaFi v3 Breaking Changes

This document tracks deprecated items that should be removed in the next major release of DeltaFi.

## MinIO Replacement Deprecations

The following items were deprecated as part of the MinIO to s3proxy migration and are retained only for backward compatibility with older plugins.

### Configuration

| Deprecated | Replacement | Notes |
|------------|-------------|-------|
| `enable.minio` | `enable.local_object_storage` | Both are currently checked; remove `enable.minio` support |

### Environment Variables

| Deprecated | Replacement | Notes |
|------------|-------------|-------|
| `SNOWBALL_ENABLED` | (none) | Hardcoded to `false`; remove entirely |
| `MINIO_URL` | (none) | Rename to `STORAGE_URL` or similar |
| `MINIO_ACCESSKEY` | (none) | Rename to `STORAGE_ACCESSKEY` or similar |
| `MINIO_SECRETKEY` | (none) | Rename to `STORAGE_SECRETKEY` or similar |
| `MINIO_PARTSIZE` | (none) | Rename to `STORAGE_PARTSIZE` or similar |

### Services / Hostnames

| Deprecated | Replacement | Notes |
|------------|-------------|-------|
| `deltafi-minio` service alias | `deltafi-s3proxy` | Remove K8s service alias and Docker Compose network alias |

### Files

| Deprecated | Replacement | Notes |
|------------|-------------|-------|
| `minio.env` | `storage.env` | Rename secrets file |

### Code

| Deprecated | Location | Notes |
|------------|----------|-------|
| `MinioProperties` class | `deltafi-common` | Rename to `StorageProperties` |
| `MinioObjectStorageService` class | `deltafi-common` | Rename to `ObjectStorageServiceImpl` or similar |
| `MinioAutoConfiguration` class | `deltafi-common` | Rename to `StorageAutoConfiguration` |
| `minio` package path | `deltafi-common` | Rename package from `s3.minio` to `s3` |

### Python SDK

| Deprecated | Location | Notes |
|------------|----------|-------|
| `MINIO_URL` env var usage | `deltafi-python/src/deltafi/plugin.py` | Update to new env var name |
| `MINIO_ACCESSKEY` env var usage | `deltafi-python/src/deltafi/plugin.py` | Update to new env var name |
| `MINIO_SECRETKEY` env var usage | `deltafi-python/src/deltafi/plugin.py` | Update to new env var name |

## S3 Client Library Migration

### Java: MinIO Client → AWS S3 SDK

The Java codebase currently uses the MinIO client library (`io.minio:minio`). This should be replaced with the AWS S3 SDK (`software.amazon.awssdk:s3`) for broader compatibility and to remove the MinIO dependency.

**Current implementation:**
```java
// deltafi-common/src/main/java/org/deltafi/common/storage/s3/minio/MinioObjectStorageService.java
import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;

minioClient.getObject(GetObjectArgs.builder()
    .bucket(bucket)
    .object(name)
    .offset(offset)
    .length(size)
    .build());
```

**Target implementation:**
```java
// deltafi-common/src/main/java/org/deltafi/common/storage/s3/S3ObjectStorageService.java
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

s3Client.getObject(GetObjectRequest.builder()
    .bucket(bucket)
    .key(name)
    .range(String.format("bytes=%d-%d", offset, offset + size - 1))
    .build());
```

**Key differences:**
| MinIO Client | AWS S3 SDK |
|--------------|------------|
| `MinioClient` | `S3Client` |
| `GetObjectArgs` | `GetObjectRequest` |
| `PutObjectArgs` | `PutObjectRequest` |
| `.object(name)` | `.key(name)` |
| `.offset(n).length(m)` | `.range("bytes=n-m")` |
| `minioClient.putObject()` | `s3Client.putObject()` |
| `minioClient.removeObject()` | `s3Client.deleteObject()` |

**Dependencies to change:**
```groovy
// Remove:
implementation 'io.minio:minio:8.x.x'

// Add:
implementation 'software.amazon.awssdk:s3:2.x.x'
```

### Python: minio → boto3

The Python SDK currently uses the `minio` library. This should be replaced with `boto3` for broader compatibility.

**Current implementation:**
```python
# deltafi-python/src/deltafi/storage.py
import minio

self.minio_client = minio.Minio(
    f"{parsed.hostname}:{str(parsed.port)}",
    access_key=access_key,
    secret_key=secret_key,
    secure=False
)

self.minio_client.get_object(bucket, key, offset, size)
self.minio_client.put_object(bucket, key, data, length)
```

**Target implementation:**
```python
# deltafi-python/src/deltafi/storage.py
import boto3
from botocore.config import Config

self.s3_client = boto3.client(
    's3',
    endpoint_url=url,
    aws_access_key_id=access_key,
    aws_secret_access_key=secret_key,
    config=Config(signature_version='s3v4')
)

self.s3_client.get_object(
    Bucket=bucket,
    Key=key,
    Range=f'bytes={offset}-{offset + size - 1}'
)
self.s3_client.put_object(Bucket=bucket, Key=key, Body=data)
```

**Key differences:**
| minio | boto3 |
|-------|-------|
| `minio.Minio()` | `boto3.client('s3', endpoint_url=...)` |
| `.get_object(bucket, key, offset, size)` | `.get_object(Bucket=, Key=, Range=)` |
| `.put_object(bucket, key, data, length)` | `.put_object(Bucket=, Key=, Body=)` |
| `.remove_object(bucket, key)` | `.delete_object(Bucket=, Key=)` |
| `.bucket_exists(bucket)` | `.head_bucket(Bucket=)` |

**Dependencies to change:**
```
# Remove:
minio>=7.0.0

# Add:
boto3>=1.26.0
```

## Migration Steps for v3

1. **Environment variables:** Update all plugins to use new environment variable names
2. **Configuration:** Remove `enable.minio` checks from Helm templates and compose.go
3. **Configuration:** Remove `SNOWBALL_ENABLED` from all configuration
4. **Services:** Remove `deltafi-minio` service alias from Kubernetes and Docker Compose
5. **Secrets:** Rename `minio.env` to `storage.env`
6. **Java code:** Rename `Minio*` classes and `s3.minio` package
7. **Java dependencies:** Replace `io.minio:minio` with `software.amazon.awssdk:s3`
8. **Python code:** Update SDK to use new env var names
9. **Python dependencies:** Replace `minio` with `boto3`
10. **Documentation:** Remove any remaining "minio" references
