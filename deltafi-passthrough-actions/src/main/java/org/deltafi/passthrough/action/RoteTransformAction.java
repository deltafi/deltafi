package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.passthrough.param.RoteTransformParameters;

@Slf4j
public class RoteTransformAction extends TransformAction<RoteTransformParameters> {
    public RoteTransformAction() {
        super(RoteTransformParameters.class);
    }

    public Result<RoteTransformParameters> execute(DeltaFile deltaFile, RoteTransformParameters params) {
        log.trace(params.getName() + " transforming (" + deltaFile.getDid() + ")");

        TransformResult<RoteTransformParameters> result =
                new TransformResult<>(deltaFile, params, params.getResultType());
        result.setObjectReference(deltaFile.getProtocolStack().get(0).getObjectReference());
        result.addMetadata(params.getStaticMetadata());
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
