package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.types.ActionEventType;

public class EgressResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    EgressResult(EgressAction action, String did) {
        super(action, did);
    }

    @Override
    final public ResultType resultType() { return ResultType.QUEUE; }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.EGRESS; }
}