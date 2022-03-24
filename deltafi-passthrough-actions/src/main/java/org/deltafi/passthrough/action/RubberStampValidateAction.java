package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.validate.SimpleValidateAction;
import org.deltafi.actionkit.action.validate.ValidateResult;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class RubberStampValidateAction extends SimpleValidateAction {
    public Result validate(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {

        return new ValidateResult(context);
    }
}