package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.enrich.EnrichAction;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.passthrough.param.RoteEnrichParameters;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Objects;

@Slf4j
@SuppressWarnings("unused")
public class RoteEnrichAction extends EnrichAction<RoteEnrichParameters> {

    @SuppressWarnings("unused")
    public RoteEnrichAction() {
        super(RoteEnrichParameters.class);
    }

    public Result execute(DeltaFile deltaFile, ActionContext actionContext, RoteEnrichParameters params) {
        log.trace(actionContext.getName() + " enrich (" + deltaFile.getDid() + ")");
        EnrichResult result = new EnrichResult(actionContext);
        if (Objects.nonNull(params.getEnrichments())) {
            params.getEnrichments().forEach((k, v) -> result.addEnrichment(k, v, MediaType.TEXT_PLAIN));
        }
        return result;
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }
}
