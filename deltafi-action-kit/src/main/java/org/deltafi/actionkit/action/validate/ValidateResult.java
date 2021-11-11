package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;

public class ValidateResult extends Result {
    public ValidateResult(DeltaFile deltaFile, ActionParameters params) {
        super(deltaFile, params);
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.VALIDATE;
    }
}