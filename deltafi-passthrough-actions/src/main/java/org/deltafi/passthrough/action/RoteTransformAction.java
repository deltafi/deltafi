package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.passthrough.param.RoteTransformParameters;

@Slf4j
public class RoteTransformAction extends TransformAction<RoteTransformParameters> {
    public RoteTransformAction() {
        super(RoteTransformParameters.class);
    }

    public Result execute(DeltaFile deltaFile, ActionContext actionContext, RoteTransformParameters params) {
        log.trace(actionContext.getName() + " transforming (" + deltaFile.getDid() + ")");

        TransformResult result =
                new TransformResult(actionContext, params.getResultType());
        result.setObjectReference(deltaFile.getProtocolStack().get(0).getObjectReference());
        return result;
    }

    @Override
    public String getConsumes() {
        return DeltaFiConstants.MATCHES_ANY;
    }

    @Override
    public String getProduces() {
        return DeltaFiConstants.MATCHES_ANY;
    }

}
