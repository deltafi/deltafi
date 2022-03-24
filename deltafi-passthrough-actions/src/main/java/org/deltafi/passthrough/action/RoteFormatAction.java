package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.format.SimpleFormatAction;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.deltafi.core.domain.generated.types.Domain;
import org.deltafi.core.domain.generated.types.Enrichment;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class RoteFormatAction extends SimpleFormatAction {
    public Result format(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull Content content, @NotNull Map<String, Domain> domains, @NotNull Map<String, Enrichment> enrichment) {
        FormatResult result = new FormatResult(context, sourceInfo.getFilename());
        result.setContentReference(content.getContentReference());
        result.addMetadata(sourceInfo.getMetadata(), "sourceInfo.");
        result.addMetadata(content.getMetadata());
        return result;
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }
}
