package org.deltafi.core.action.delete;

import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventType;

public class DeleteResult extends Result {

    public DeleteResult(ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public ActionEventType actionEventType() {
        return ActionEventType.DELETE;
    }
}