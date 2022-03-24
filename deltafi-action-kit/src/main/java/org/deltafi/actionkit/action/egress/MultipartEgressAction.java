package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("unused")
public abstract class MultipartEgressAction<P extends EgressActionParameters> extends EgressActionBase<P> {
    public MultipartEgressAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    protected final Result execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull P params) {
        return egress(context, params, deltaFile.getSourceInfo(), deltaFile.getFormattedData());
    }

    public abstract Result egress(@NotNull ActionContext context, @NotNull P params, @NotNull SourceInfo sourceInfo, @NotNull List<FormattedData> formattedDataList);
}
