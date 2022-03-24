package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

public abstract class ValidateAction<P extends ActionParameters> extends ValidateActionBase<P> {
    public ValidateAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    public final Result execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull P params) {
        return validate(context, params, deltaFile.getSourceInfo(), deltaFile.getFormattedData().get(0));
    }

    public abstract Result validate(@NotNull ActionContext context, @NotNull P params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData);
}
