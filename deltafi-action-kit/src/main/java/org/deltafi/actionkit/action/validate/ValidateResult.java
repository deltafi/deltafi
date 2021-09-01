package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.generated.types.ActionEventType;

public class ValidateResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    public ValidateResult(String name, String did) {
        super(name, did);
    }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.VALIDATE; }
}