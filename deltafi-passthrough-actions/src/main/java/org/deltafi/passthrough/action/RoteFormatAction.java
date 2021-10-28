package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.format.FormatAction;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;

@Slf4j
public class RoteFormatAction extends FormatAction<ActionParameters> {
    public Result execute(DeltaFile deltafile, ActionParameters params) {
        log.trace(params.getName() + " formatting (" + deltafile.getDid() + ")");

        FormatResult result = new FormatResult(params.getName(), deltafile.getDid(), deltafile.getSourceInfo().getFilename());

        deltafile.getSourceInfo().getMetadata().forEach(kv -> result.addMetadata("sourceInfo." + kv.getKey(), kv.getValue()));
        params.getStaticMetadata().forEach(result::addMetadata);

        result.setObjectReference(deltafile.getProtocolStack().get(0).getObjectReference());

        logFilesProcessedMetric(ActionEventType.FORMAT, deltafile);

        return result;
    }

    @Override
    public Class<ActionParameters> getParamType() {
        return ActionParameters.class;
    }
}