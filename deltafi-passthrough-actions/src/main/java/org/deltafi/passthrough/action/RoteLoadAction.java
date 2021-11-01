package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.load.LoadAction;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.passthrough.param.RoteLoadParameters;

@Slf4j
public class RoteLoadAction extends LoadAction<RoteLoadParameters> {
    public RoteLoadAction() {
        super(RoteLoadParameters.class);
    }

    public Result<RoteLoadParameters> execute(DeltaFile deltaFile, RoteLoadParameters params) {
        log.trace(params.getName() + " loading (" + deltaFile.getDid() + ")");

        LoadResult<RoteLoadParameters> result = new LoadResult<>(deltaFile, params);
        params.getDomains().forEach(result::addDomain);
        return result;
    }
}