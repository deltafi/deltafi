package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.api.types.Content;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public abstract class MultipartTransformAction<P extends ActionParameters> extends TransformActionBase<P> {
    public MultipartTransformAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    protected final Result execute(@NotNull DeltaFile deltaFile,
                                   @NotNull ActionContext context,
                                   @NotNull P params) {
        return transform(context,
                params,
                deltaFile.getSourceInfo(),
                deltaFile.getLastProtocolLayerContent(),
                deltaFile.getLastProtocolLayerMetadataAsMap());
    }

    public abstract Result transform(@NotNull ActionContext context,
                                     @NotNull P params,
                                     @NotNull SourceInfo sourceInfo,
                                     @NotNull List<Content> contentList,
                                     @NotNull Map<String, String> metadata);
}
