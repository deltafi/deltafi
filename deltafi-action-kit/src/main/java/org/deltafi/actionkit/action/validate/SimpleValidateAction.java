package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

public abstract class SimpleValidateAction extends ValidateAction<ActionParameters> {
    public SimpleValidateAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result validate(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        return validate(context, sourceInfo, formattedData);
    }

    public abstract Result validate(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData);
}
