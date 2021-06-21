package org.deltafi.ingress.exceptions;

public class DeltafiGraphQLException extends RuntimeException {

    public DeltafiGraphQLException(String message) {
        super(message);
    }

    public DeltafiGraphQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
