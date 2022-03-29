package org.deltafi.actionkit.action.format;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.api.types.Content;
import org.deltafi.core.domain.generated.types.Domain;
import org.deltafi.core.domain.generated.types.Enrichment;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class FormatAction<P extends ActionParameters> extends FormatActionBase<P> {
    public FormatAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    protected final Result execute(@NotNull DeltaFile deltaFile,
                                   @NotNull ActionContext context,
                                   @NotNull P params) {
        return format(context,
                params,
                deltaFile.getSourceInfo(),
                deltaFile.getLastProtocolLayerContent().get(0),
                deltaFile.getLastProtocolLayerMetadataAsMap(),
                deltaFile.domainMap(),
                deltaFile.enrichmentMap());
    }

    public abstract Result format(@NotNull ActionContext context,
                                  @NotNull P params,
                                  @NotNull SourceInfo sourceInfo,
                                  @NotNull Content content,
                                  @NotNull Map<String, String> metadata,
                                  @NotNull Map<String, Domain> domains,
                                  @NotNull Map<String, Enrichment> enrichment);
}
