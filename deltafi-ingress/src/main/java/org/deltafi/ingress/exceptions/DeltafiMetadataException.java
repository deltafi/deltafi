package org.deltafi.ingress.exceptions;

public class DeltafiMetadataException extends RuntimeException {

    public DeltafiMetadataException(String message) {
        super(message);
    }

    public DeltafiMetadataException(String message, Throwable cause) {
        super(message, cause);
    }
}
