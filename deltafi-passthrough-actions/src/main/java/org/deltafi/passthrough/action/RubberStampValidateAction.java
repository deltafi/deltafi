package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.validate.SimpleValidateAction;
import org.deltafi.actionkit.action.validate.ValidateResult;
import org.deltafi.core.domain.api.types.DeltaFile;

@Slf4j
public class RubberStampValidateAction extends SimpleValidateAction {
    public Result execute(DeltaFile deltaFile, ActionParameters params) {
        log.trace(params.getName() + " validating (" + deltaFile.getDid() + ")");
        return new ValidateResult(deltaFile, params);
    }
}