package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.generated.types.ActionEventType;

public abstract class LoadAction<P extends ActionParameters> extends Action<P> {
    public LoadAction(Class<P> actionParametersClass) {
        super(actionParametersClass, ActionEventType.LOAD);
    }
}
