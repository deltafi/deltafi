package org.deltafi.core.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.load.MultipartLoadAction;
import org.deltafi.actionkit.action.load.SplitResult;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.api.types.Content;
import org.deltafi.core.parameters.SplitterLoadParameters;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class SplitterLoadAction extends MultipartLoadAction<SplitterLoadParameters> {

    final public String CONSUMES = "binary";

    public SplitterLoadAction() {
        super(SplitterLoadParameters.class);
    }

    @Override
    public Result load(@NotNull ActionContext context,
                       @NotNull SplitterLoadParameters params,
                       @NotNull SourceInfo sourceInfo,
                       @NotNull List<Content> contentList,
                       @NotNull Map<String, String> metadata) {
        SplitResult result = new SplitResult(context);

        for (Content content : contentList) {
            result.addChild(content.getName(),
                    params.getReinjectFlow(),
                    sourceInfo.getMetadata(),
                    Collections.singletonList(content));
        }

        return result;
    }

    @Override
    public String getConsumes() {
        return CONSUMES;
    }
}
