package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

public abstract class SimpleEgressAction extends EgressAction<EgressActionParameters> {
    public SimpleEgressAction() {
        super(EgressActionParameters.class);
    }

    @Override
    public final Result egress(@NotNull ActionContext context, @NotNull EgressActionParameters params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        return egress(context, sourceInfo, formattedData);
    }

    public abstract Result egress(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData);
}
