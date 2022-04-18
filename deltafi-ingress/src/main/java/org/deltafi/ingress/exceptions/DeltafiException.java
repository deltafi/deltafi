package org.deltafi.ingress.exceptions;

public class DeltafiException extends Exception {
    public DeltafiException(String message) { super(message); }
    public DeltafiException(String message, Throwable t) { super(message, t); }
}
