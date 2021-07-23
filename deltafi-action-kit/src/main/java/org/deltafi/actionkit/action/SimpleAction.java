package org.deltafi.actionkit.action;

import org.deltafi.actionkit.action.parameters.ActionParameters;

public abstract class SimpleAction extends Action<ActionParameters> {

    @Override
    public Class<ActionParameters> getParamType() {
        return ActionParameters.class;
    }
}
