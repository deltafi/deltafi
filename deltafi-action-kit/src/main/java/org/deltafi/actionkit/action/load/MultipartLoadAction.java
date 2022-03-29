package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.api.types.Content;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public abstract class MultipartLoadAction<P extends ActionParameters> extends LoadActionBase<P> {
    public MultipartLoadAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    public abstract String getConsumes();

    @Override
    protected final Result execute(@NotNull DeltaFile deltaFile,
                                   @NotNull ActionContext context,
                                   @NotNull P params) {
        return load(context,
                params,
                deltaFile.getSourceInfo(),
                deltaFile.getLastProtocolLayerContent(),
                deltaFile.getLastProtocolLayerMetadataAsMap());
    }

    public abstract Result load(@NotNull ActionContext context,
                                @NotNull P params,
                                @NotNull SourceInfo sourceInfo,
                                @NotNull List<Content> contentList,
                                @NotNull Map<String, String> metadata);
}
