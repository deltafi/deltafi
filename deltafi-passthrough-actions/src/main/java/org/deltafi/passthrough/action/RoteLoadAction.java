package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.action.load.MultipartLoadAction;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.api.types.Content;
import org.deltafi.passthrough.param.RoteLoadParameters;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class RoteLoadAction extends MultipartLoadAction<RoteLoadParameters> {
    public RoteLoadAction() {
        super(RoteLoadParameters.class);
    }

    @Override
    public Result load(@NotNull ActionContext context,
                       @NotNull RoteLoadParameters params,
                       @NotNull SourceInfo sourceInfo,
                       @NotNull List<Content> contentList,
                       @NotNull Map<String, String> metadata) {
        LoadResult result = new LoadResult(context, contentList);
        params.getDomains().forEach(d -> result.addDomain(d, null, MediaType.TEXT_PLAIN));
        return result;
    }
    @Override
    public String getConsumes() {
        return DeltaFiConstants.MATCHES_ANY;
    }
}
