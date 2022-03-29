package org.deltafi.actionkit.action.format;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.deltafi.core.domain.generated.types.Domain;
import org.deltafi.core.domain.generated.types.Enrichment;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class SimpleFormatAction extends FormatAction<ActionParameters> {
    public SimpleFormatAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result format(@NotNull ActionContext context,
                               @NotNull ActionParameters params,
                               @NotNull SourceInfo sourceInfo,
                               @NotNull Content content,
                               @NotNull Map<String, String> metadata,
                               @NotNull Map<String, Domain> domains,
                               @NotNull Map<String, Enrichment> enrichment) {
        return format(context, sourceInfo, content, metadata, domains, enrichment);
    }

    public abstract Result format(@NotNull ActionContext context,
                                  @NotNull SourceInfo sourceInfo,
                                  @NotNull Content content,
                                  @NotNull Map<String, String> metadata,
                                  @NotNull Map<String, Domain> domains,
                                  @NotNull Map<String, Enrichment> enrichment);
}
