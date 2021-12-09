package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;

public abstract class ValidateAction<P extends ActionParameters> extends Action<P> {
    public ValidateAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }
}
