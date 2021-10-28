package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.SimpleAction;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.validate.ValidateResult;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;

@Slf4j
public class RubberStampValidateAction extends SimpleAction {
    public Result execute(DeltaFile deltafile, ActionParameters params) {
        log.trace(params.getName() + " validating (" + deltafile.getDid() + ")");

        logFilesProcessedMetric(ActionEventType.VALIDATE, deltafile);

        return new ValidateResult(params.getName(), deltafile.getDid());
    }
}