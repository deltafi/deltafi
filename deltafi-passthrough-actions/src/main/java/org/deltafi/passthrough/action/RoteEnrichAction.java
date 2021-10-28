package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.SimpleAction;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;

@Slf4j
public class RoteEnrichAction extends SimpleAction {
    public Result execute(DeltaFile deltafile, ActionParameters params) {
        log.trace(params.getName() + " enrich (" + deltafile.getDid() + ")");

        logFilesProcessedMetric(ActionEventType.ENRICH, deltafile);

        return new EnrichResult(params.getName(), deltafile.getDid());
    }
}