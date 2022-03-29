package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.enrich.EnrichAction;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.deltafi.core.domain.generated.types.Domain;
import org.deltafi.core.domain.generated.types.Enrichment;
import org.deltafi.passthrough.param.RoteEnrichParameters;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unused")
public class RoteEnrichAction extends EnrichAction<RoteEnrichParameters> {

    @SuppressWarnings("unused")
    public RoteEnrichAction() {
        super(RoteEnrichParameters.class);
    }

    public Result enrich(@NotNull ActionContext context,
                         @NotNull RoteEnrichParameters params,
                         @NotNull SourceInfo sourceInfo,
                         @NotNull Content content,
                         @NotNull Map<String, String> metadata,
                         @NotNull Map<String, Domain> domainList,
                         @NotNull Map<String, Enrichment> enrichmentList) {
        EnrichResult result = new EnrichResult(context);
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
