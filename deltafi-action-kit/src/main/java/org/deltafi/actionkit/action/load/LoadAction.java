package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class LoadAction<P extends ActionParameters> extends LoadActionBase<P> {
    public LoadAction(Class<P> actionParametersClass) {
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
                deltaFile.getLastProtocolLayerContent().get(0),
                deltaFile.getLastProtocolLayerMetadataAsMap());
    }

    public abstract Result load(@NotNull ActionContext context,
                                @NotNull P params,
                                @NotNull SourceInfo sourceInfo,
                                @NotNull Content content,
                                @NotNull Map<String, String> metadata);
}
