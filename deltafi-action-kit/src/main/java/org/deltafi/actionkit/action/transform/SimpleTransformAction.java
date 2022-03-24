package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.jetbrains.annotations.NotNull;

public abstract class SimpleTransformAction extends TransformAction<ActionParameters> {
    public SimpleTransformAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result transform(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull SourceInfo sourceInfo, @NotNull Content content) {
        return transform(context, sourceInfo, content);
    }

    public abstract Result transform(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull Content content);
}
