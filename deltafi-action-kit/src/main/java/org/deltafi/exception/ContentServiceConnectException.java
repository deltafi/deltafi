package org.deltafi.exception;

public class ContentServiceConnectException extends RuntimeException {
    public ContentServiceConnectException(Exception e) {
        super(e);
    }
}