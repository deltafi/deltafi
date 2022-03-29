package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.transform.MultipartTransformAction;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.deltafi.passthrough.param.RoteTransformParameters;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class RoteTransformAction extends MultipartTransformAction<RoteTransformParameters> {
    public RoteTransformAction() {
        super(RoteTransformParameters.class);
    }

    @Override
    public Result transform(@NotNull ActionContext context,
                            @NotNull RoteTransformParameters params,
                            @NotNull SourceInfo sourceInfo,
                            @NotNull List<Content> contentList,
                            @NotNull Map<String, String> metadata) {
        TransformResult result = new TransformResult(context, params.getResultType());
        result.setContent(contentList);
        return result;
    }

    @Override
    public String getConsumes() {
        return DeltaFiConstants.MATCHES_ANY;
    }

    @Override
    public String getProduces() {
        return DeltaFiConstants.MATCHES_ANY;
    }

}
