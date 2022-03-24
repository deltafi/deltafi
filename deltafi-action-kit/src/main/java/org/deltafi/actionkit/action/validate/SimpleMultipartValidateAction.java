package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("unused")
public abstract class SimpleMultipartValidateAction extends MultipartValidateAction<ActionParameters> {
    public SimpleMultipartValidateAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result validate(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull SourceInfo sourceInfo, @NotNull List<FormattedData> formattedDataList) {
        return validate(context, sourceInfo, formattedDataList);
    }

    public abstract Result validate(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull List<FormattedData> formattedDataList);
}
