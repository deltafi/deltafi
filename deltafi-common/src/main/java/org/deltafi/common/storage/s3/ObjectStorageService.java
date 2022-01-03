package org.deltafi.common.storage.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;

public interface ObjectStorageService {
    default List<String> getObjectNames(String bucket, String prefix) {
        return getObjectNames(bucket, prefix, null);
    }

    List<String> getObjectNames(String bucket, String prefix, ZonedDateTime lastModifiedBefore);

    InputStream getObject(ObjectReference objectReference) throws ObjectStorageException;

    default byte[] getObjectAsByteArray(ObjectReference objectReference) throws ObjectStorageException, IOException {
        return getObject(objectReference).readAllBytes();
    }

    ObjectReference putObject(ObjectReference objectReference, InputStream inputStream) throws ObjectStorageException;

    default ObjectReference putObjectAsByteArray(ObjectReference objectReference, byte[] object) throws ObjectStorageException {
        return putObject(objectReference, new ByteArrayInputStream(object));
    }

    void removeObject(ObjectReference objectReference);

    boolean removeObjects(String bucket, String prefix);

    long getObjectSize(String bucket, String name);
}
