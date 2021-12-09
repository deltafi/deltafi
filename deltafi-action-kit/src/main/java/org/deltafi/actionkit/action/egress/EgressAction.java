package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Action;

public abstract class EgressAction<P extends EgressActionParameters> extends Action<P> {
    public EgressAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }
}
