package org.deltafi.dgs.exceptions;

import java.util.List;

public class UnexpectedActionException extends RuntimeException {
    public UnexpectedActionException(String actionName, String did, List<String> nextActions) {
        super("Unexpected action " + actionName + " performed on DeltaFile " + did + ". " +
                (nextActions.isEmpty() ? "No Actions are currently expected." : "Expected one of [" + String.join(", ", nextActions) + "]"));
    }
}
