package org.deltafi.core.action.delete;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;

public class DeleteResult extends Result<ActionParameters> {
    public DeleteResult(DeltaFile deltaFile, ActionParameters params) {
        super(deltaFile, params);
    }

    @Override
    public ActionEventType actionEventType() {
        return ActionEventType.DELETE;
    }
}