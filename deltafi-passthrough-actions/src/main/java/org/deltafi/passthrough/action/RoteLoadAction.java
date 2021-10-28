package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.passthrough.param.RoteLoadParameters;

@Slf4j
public class RoteLoadAction extends Action<RoteLoadParameters> {

    public Result execute(DeltaFile deltafile, RoteLoadParameters params) {
        log.trace(params.getName() + " loading (" + deltafile.getDid() + ")");

        LoadResult result = new LoadResult(params.getName(), deltafile.getDid());

        params.getDomains().forEach(result::addDomain);

        logFilesProcessedMetric(ActionEventType.LOAD, deltafile);

        return result;
    }

    @Override
    public Class<RoteLoadParameters> getParamType() {
        return RoteLoadParameters.class;
    }
}