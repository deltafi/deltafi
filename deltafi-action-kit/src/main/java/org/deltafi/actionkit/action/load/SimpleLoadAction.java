package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.parameters.ActionParameters;

public abstract class SimpleLoadAction extends LoadAction<ActionParameters> {
    public SimpleLoadAction() {
        super(ActionParameters.class);
    }
}
