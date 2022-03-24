package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.jetbrains.annotations.NotNull;

public abstract class SimpleLoadAction extends LoadAction<ActionParameters> {
    public SimpleLoadAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result load(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull SourceInfo sourceInfo, @NotNull Content content) {
        return load(context, sourceInfo, content);
    }

    public abstract Result load(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull Content content);
}
