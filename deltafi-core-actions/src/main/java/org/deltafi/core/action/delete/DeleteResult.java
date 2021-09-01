package org.deltafi.core.action.delete;

import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.generated.types.ActionEventType;

public class DeleteResult extends Result {
    public DeleteResult(String name, String did) {
        super(name, did);
    }

    @Override
    public ActionEventType actionEventType() {
        return ActionEventType.DELETE;
    }
}