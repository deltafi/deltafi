package org.deltafi.dgs.exceptions;

import org.deltafi.dgs.generated.types.ActionEventType;

public class UnknownTypeException extends RuntimeException {
    public UnknownTypeException(String actionName, String did, ActionEventType type) {
        super("Unknown action type " + type + " performed on DeltaFile " + did + " by  " + actionName);
    }
}
