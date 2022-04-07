package org.deltafi.actionkit.action.delete;

import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventType;

public class DeleteResult extends Result {
    public DeleteResult(ActionContext context) {
        super(context);
    }

    @Override
    public ActionEventType actionEventType() {
        return ActionEventType.DELETE;
    }
}