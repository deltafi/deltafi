package org.deltafi.common.storage.s3;

public class ObjectStorageException extends Exception {
    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
