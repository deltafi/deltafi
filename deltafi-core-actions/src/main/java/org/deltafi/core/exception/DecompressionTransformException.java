package org.deltafi.core.exception;

public class DecompressionTransformException extends RuntimeException {
    public DecompressionTransformException(String reason, Throwable e) {
        super(reason, e);
    }
}
