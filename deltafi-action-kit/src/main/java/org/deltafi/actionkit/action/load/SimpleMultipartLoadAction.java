package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("unused")
public abstract class SimpleMultipartLoadAction extends MultipartLoadAction<ActionParameters> {
    public SimpleMultipartLoadAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result load(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull SourceInfo sourceInfo, @NotNull List<Content> contentList) {
        return load(context, sourceInfo, contentList);
    }

    public abstract Result load(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull List<Content> contentList);
}
