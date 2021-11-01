package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.generated.types.ActionEventType;

public abstract class ValidateAction<P extends ActionParameters> extends Action<P> {
    public ValidateAction(Class<P> actionParametersClass) {
        super(actionParametersClass, ActionEventType.VALIDATE);
    }
}
