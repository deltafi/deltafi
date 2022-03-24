package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

public abstract class EgressAction<P extends EgressActionParameters> extends EgressActionBase<P> {
    public EgressAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    protected final Result execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull P params) {
        return egress(context, params, deltaFile.getSourceInfo(), deltaFile.getFormattedData().get(0));
    }

    public abstract Result egress(@NotNull ActionContext context, @NotNull P params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData);
}
