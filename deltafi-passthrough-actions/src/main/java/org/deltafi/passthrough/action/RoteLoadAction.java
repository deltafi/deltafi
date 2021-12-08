package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.load.LoadAction;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.passthrough.param.RoteLoadParameters;
@Slf4j
public class RoteLoadAction extends LoadAction<RoteLoadParameters> {
    public RoteLoadAction() {
        super(RoteLoadParameters.class);
    }

    public Result execute(DeltaFile deltaFile, ActionContext actionContext, RoteLoadParameters params) {
        log.trace(actionContext.getName() + " loading (" + deltaFile.getDid() + ")");

        LoadResult result = new LoadResult(actionContext, deltaFile);
        params.getDomains().forEach(result::addDomain);
        return result;
    }
    @Override
    public String getConsumes() {
        return DeltaFiConstants.MATCHES_ANY;
    }
}
