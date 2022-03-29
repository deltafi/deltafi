package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.api.types.Content;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public abstract class SimpleMultipartLoadAction extends MultipartLoadAction<ActionParameters> {
    public SimpleMultipartLoadAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result load(@NotNull ActionContext context,
                             @NotNull ActionParameters params,
                             @NotNull SourceInfo sourceInfo,
                             @NotNull List<Content> contentList,
                             @NotNull Map<String, String> metadata) {
        return load(context, sourceInfo, contentList, metadata);
    }

    public abstract Result load(@NotNull ActionContext context,
                                @NotNull SourceInfo sourceInfo,
                                @NotNull List<Content> contentList,
                                @NotNull Map<String, String> metadata);
}
