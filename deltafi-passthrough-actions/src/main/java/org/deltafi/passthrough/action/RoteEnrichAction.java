package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.enrich.EnrichAction;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;

@Slf4j
public class RoteEnrichAction extends EnrichAction<ActionParameters> {
    public RoteEnrichAction() {
        super(ActionParameters.class);
    }

    public Result<ActionParameters> execute(DeltaFile deltaFile, ActionParameters params) {
        log.trace(params.getName() + " enrich (" + deltaFile.getDid() + ")");
        return new EnrichResult(deltaFile, params);
    }
}