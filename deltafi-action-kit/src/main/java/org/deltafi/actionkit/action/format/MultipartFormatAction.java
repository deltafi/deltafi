package org.deltafi.actionkit.action.format;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.deltafi.core.domain.generated.types.Domain;
import org.deltafi.core.domain.generated.types.Enrichment;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public abstract class MultipartFormatAction<P extends ActionParameters> extends FormatActionBase<P> {
    public MultipartFormatAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    protected final Result execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull P params) {

        return format(context, params, deltaFile.getSourceInfo(), deltaFile.getLastProtocolLayerContent(), deltaFile.domainMap(), deltaFile.enrichmentMap());
    }

    public abstract Result format(@NotNull ActionContext context, @NotNull P params, @NotNull SourceInfo sourceInfo, @NotNull List<Content> contentList, @NotNull Map<String, Domain> domains, @NotNull Map<String, Enrichment> enrichment);
}