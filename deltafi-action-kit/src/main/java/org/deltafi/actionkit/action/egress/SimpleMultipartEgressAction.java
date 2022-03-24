package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("unused")
public abstract class SimpleMultipartEgressAction extends MultipartEgressAction<EgressActionParameters> {
    public SimpleMultipartEgressAction() {
        super(EgressActionParameters.class);
    }

    @Override
    public final Result egress(@NotNull ActionContext context, @NotNull EgressActionParameters params, @NotNull SourceInfo sourceInfo, @NotNull List<FormattedData> formattedDataList) {
        return egress(context, sourceInfo, formattedDataList);
    }

    public abstract Result egress(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull List<FormattedData> formattedDataList);
}
