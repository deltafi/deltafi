package org.deltafi.actionkit.action.enrich;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.generated.types.ActionEventType;

public abstract class EnrichAction<P extends ActionParameters> extends Action<P> {
    public EnrichAction(Class<P> actionParametersClass) {
        super(actionParametersClass, ActionEventType.ENRICH);
    }
}
