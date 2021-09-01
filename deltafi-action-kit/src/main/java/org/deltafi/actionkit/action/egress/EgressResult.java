package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.generated.types.ActionEventType;

public class EgressResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    public EgressResult(String name, String did) {
        super(name, did);
    }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.EGRESS; }
}