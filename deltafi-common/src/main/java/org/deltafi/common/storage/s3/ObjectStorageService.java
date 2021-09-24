package org.deltafi.common.storage.s3;

import io.minio.ObjectWriteResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;

public interface ObjectStorageService {
    default List<String> getObjectNames(String bucket, String prefix) {
        return getObjectNames(bucket, prefix, null);
    }

    List<String> getObjectNames(String bucket, String prefix, ZonedDateTime lastModifiedBefore);

    InputStream getObjectAsInputStream(String bucket, String name, long offset, long length) throws ObjectStorageException;

    byte[] getObject(String bucket, String name, long offset, long length) throws ObjectStorageException;

    default ObjectWriteResponse putObject(String bucket, String name, byte[] object) throws ObjectStorageException {
        return putObject(bucket, name, new ByteArrayInputStream(object), object.length);
    }

    ObjectWriteResponse putObject(String bucket, String name, InputStream inputStream, long size) throws ObjectStorageException;

    void removeObject(String bucket, String name);

    boolean removeObjects(String bucket, String prefix);

    long getObjectSize(ObjectWriteResponse writeResponse);
}
