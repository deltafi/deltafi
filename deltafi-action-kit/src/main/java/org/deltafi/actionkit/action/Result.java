package org.deltafi.actionkit.action;

import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;

import java.time.OffsetDateTime;

public abstract class Result {

    protected ActionContext actionContext;

    public Result(ActionContext actionContext) {
        this.actionContext = actionContext;
    }

    public abstract ActionEventType actionEventType();

    public ActionEventInput toEvent() {
        return ActionEventInput.newBuilder()
                .did(actionContext.getDid())
                .action(actionContext.getName())
                .time(OffsetDateTime.now())
                .type(actionEventType())
                .build();
    }

    public ActionContext getActionContext() {
        return actionContext;
    }

}
