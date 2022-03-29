package org.deltafi.actionkit.action.enrich;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class EnrichAction<P extends ActionParameters> extends EnrichActionBase<P> {
    public EnrichAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    protected final Result execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull P params) {
        return enrich(context,
                params,
                deltaFile.getSourceInfo(),
                deltaFile.getLastProtocolLayerContent().get(0),
                deltaFile.getLastProtocolLayerMetadataAsMap(),
                deltaFile.domainMap(),
                deltaFile.enrichmentMap());
    }

    public abstract Result enrich(@NotNull ActionContext context,
                                  @NotNull P params,
                                  @NotNull SourceInfo sourceInfo,
                                  @NotNull Content content,
                                  @NotNull Map<String, String> metadata,
                                  @NotNull Map<String, Domain> domains,
                                  @NotNull Map<String, Enrichment> enrichment);
}
