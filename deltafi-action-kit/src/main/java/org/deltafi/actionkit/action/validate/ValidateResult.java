package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.types.ActionEventType;

public class ValidateResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    public ValidateResult(String name, String did) {
        super(name, did);
    }

    @Override
    final public ResultType resultType() { return ResultType.QUEUE; }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.VALIDATE; }
}