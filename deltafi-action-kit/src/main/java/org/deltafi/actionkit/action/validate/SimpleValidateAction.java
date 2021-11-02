package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.parameters.ActionParameters;

public abstract class SimpleValidateAction extends ValidateAction<ActionParameters> {
    public SimpleValidateAction() {
        super(ActionParameters.class);
    }
}
