package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.passthrough.param.RoteTransformParameters;

@Slf4j
public class RoteTransformAction extends Action<RoteTransformParameters> {

    public Result execute(DeltaFile deltafile, RoteTransformParameters params) {
        log.trace(params.getName() + " transforming (" + deltafile.getDid() + ")");

        TransformResult result = new TransformResult(params.getName(), deltafile.getDid());

        result.setType(params.getResultType());
        result.setObjectReference(deltafile.getProtocolStack().get(0).getObjectReference());
        result.addMetadata(params.getStaticMetadata());

        logFilesProcessedMetric(ActionEventType.TRANSFORM, deltafile);

        return result;
    }

    @Override
    public Class<RoteTransformParameters> getParamType() {
        return RoteTransformParameters.class;
    }
}