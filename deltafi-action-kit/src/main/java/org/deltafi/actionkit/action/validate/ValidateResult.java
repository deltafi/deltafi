package org.deltafi.actionkit.action.validate;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.jetbrains.annotations.NotNull;

@Getter
@EqualsAndHashCode(callSuper = true)
public class ValidateResult extends Result {

    public ValidateResult(@NotNull ActionContext context) {
        super(context);
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.VALIDATE;
    }
}