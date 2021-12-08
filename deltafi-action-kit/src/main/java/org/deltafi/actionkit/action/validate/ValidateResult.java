package org.deltafi.actionkit.action.validate;

import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventType;

@EqualsAndHashCode(callSuper = true)
public class ValidateResult extends Result {

    public ValidateResult(ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.VALIDATE;
    }
}