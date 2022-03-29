package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.api.types.Content;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class TransformAction<P extends ActionParameters> extends TransformActionBase<P> {
    public TransformAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    protected final Result execute(@NotNull DeltaFile deltaFile,
                                   @NotNull ActionContext context,
                                   @NotNull P params) {

        return transform(context,
                params,
                deltaFile.getSourceInfo(),
                deltaFile.getLastProtocolLayerContent().get(0),
                deltaFile.getLastProtocolLayerMetadataAsMap());
    }

    public abstract Result transform(@NotNull ActionContext context,
                                     @NotNull P params,
                                     @NotNull SourceInfo sourceInfo,
                                     @NotNull Content content,
                                     @NotNull Map<String, String> metadata);
}
