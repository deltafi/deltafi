package org.deltafi.core.exception;

import org.deltafi.core.parameters.DecompressionType;

public class DecompressionTransformException extends RuntimeException {
    public DecompressionTransformException(String reason, Throwable e) {
        super(reason, e);
    }

    public DecompressionTransformException(String reason) {
        super(reason);
    }
}
