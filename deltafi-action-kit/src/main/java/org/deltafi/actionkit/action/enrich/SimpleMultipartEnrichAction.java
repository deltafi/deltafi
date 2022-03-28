package org.deltafi.actionkit.action.enrich;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.deltafi.core.domain.generated.types.Domain;
import org.deltafi.core.domain.generated.types.Enrichment;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public abstract class SimpleMultipartEnrichAction extends MultipartEnrichAction<ActionParameters> {
    public SimpleMultipartEnrichAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result enrich(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull SourceInfo sourceInfo, @NotNull List<Content> contentList, @NotNull Map<String, Domain> domains, @NotNull Map<String, Enrichment> enrichment) {
        return enrich(context, sourceInfo, contentList, domains, enrichment);
    }

    public abstract Result enrich(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull List<Content> contentList, @NotNull Map<String, Domain> domains, @NotNull Map<String, Enrichment> enrichment);
}