package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.actionkit.action.enrich.SimpleEnrichAction;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.DeltaFile;

import java.util.List;

@Slf4j
public class RoteEnrichAction extends SimpleEnrichAction {
    public Result execute(DeltaFile deltaFile, ActionParameters params) {
        log.trace(params.getName() + " enrich (" + deltaFile.getDid() + ")");
        return new EnrichResult(deltaFile, params);
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }
}
