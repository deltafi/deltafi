package org.deltafi.actionkit.action.format;

import org.deltafi.actionkit.action.parameters.ActionParameters;

public abstract class SimpleFormatAction extends FormatAction<ActionParameters> {
    public SimpleFormatAction() {
        super(ActionParameters.class);
    }
}
