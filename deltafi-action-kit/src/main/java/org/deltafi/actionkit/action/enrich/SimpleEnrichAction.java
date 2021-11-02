package org.deltafi.actionkit.action.enrich;

import org.deltafi.actionkit.action.parameters.ActionParameters;

public abstract class SimpleEnrichAction extends EnrichAction<ActionParameters> {
    public SimpleEnrichAction() {
        super(ActionParameters.class);
    }
}
