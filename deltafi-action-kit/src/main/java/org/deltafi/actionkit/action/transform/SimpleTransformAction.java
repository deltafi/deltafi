package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.parameters.ActionParameters;

public abstract class SimpleTransformAction extends TransformAction<ActionParameters> {
    public SimpleTransformAction() {
        super(ActionParameters.class);
    }
}
