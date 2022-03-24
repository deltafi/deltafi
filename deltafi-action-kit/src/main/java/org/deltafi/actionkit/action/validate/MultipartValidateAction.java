package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
public abstract class MultipartValidateAction<P extends ActionParameters> extends ValidateActionBase<P> {
    public MultipartValidateAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    public final Result execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull P params) {
        return validate(context, params, deltaFile.getSourceInfo(), Objects.isNull(deltaFile.getFormattedData()) ? Collections.emptyList() : deltaFile.getFormattedData());
    }

    public abstract Result validate(@NotNull ActionContext context, @NotNull P params, @NotNull SourceInfo sourceInfo, @NotNull List<FormattedData> formattedDataList);
}
