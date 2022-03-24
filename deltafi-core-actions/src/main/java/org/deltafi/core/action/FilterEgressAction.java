package org.deltafi.core.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.SimpleEgressAction;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class FilterEgressAction extends SimpleEgressAction {
    @Override
    public Result egress(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        return new FilterResult(context, "filtered");
    }
}
