package org.deltafi.commonactions.action.delete;

import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.types.ActionEventType;

public class DeleteResult extends Result {
    public DeleteResult(String name, String did) {
        super(name, did);
    }

    @Override
    public ActionEventType actionEventType() {
        return ActionEventType.DELETE;
    }
}
