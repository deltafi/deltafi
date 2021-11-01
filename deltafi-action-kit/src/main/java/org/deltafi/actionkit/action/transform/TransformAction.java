package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.generated.types.ActionEventType;

public abstract class TransformAction<P extends ActionParameters> extends Action<P> {
    public TransformAction(Class<P> actionParametersClass) {
        super(actionParametersClass, ActionEventType.TRANSFORM);
    }
}
