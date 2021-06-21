package org.deltafi.exception;

public class DgsPostException extends RuntimeException {
    public DgsPostException(String reason) {
        super(reason);
    }
}