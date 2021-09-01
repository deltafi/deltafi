package org.deltafi.core.domain.exceptions;

public class ActionConfigException extends Exception {

    final private String actionName;

    public ActionConfigException(String actionName, String message) {
        super(message);
        this.actionName = actionName;
    }

    public String getActionName() {
        return actionName;
    }
}