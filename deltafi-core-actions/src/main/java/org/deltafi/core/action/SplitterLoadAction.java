package org.deltafi.core.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.load.MultipartLoadAction;
import org.deltafi.actionkit.action.load.SplitResult;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.deltafi.core.domain.generated.types.ContentInput;
import org.deltafi.core.parameters.SplitterLoadParameters;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class SplitterLoadAction extends MultipartLoadAction<SplitterLoadParameters> {

    final public String CONSUMES = "binary";

    public SplitterLoadAction() {
        super(SplitterLoadParameters.class);
    }

    @Override
    public Result load(@NotNull ActionContext context,
                       @NotNull SplitterLoadParameters params,
                       @NotNull SourceInfo sourceInfo,
                       @NotNull List<Content> contentList) {

        SplitResult result = new SplitResult(context);

        for (Content content : contentList) {
            ContentInput contentInput = new ContentInput(content.getName(), content.getMetadata(), content.getContentReference());
            result.addChild(content.getName(),
                    params.getReinjectFlow(),
                    sourceInfo.getMetadata(),
                    Collections.singletonList(contentInput));
        }

        return result;
    }

    @Override
    public String getConsumes() {
        return CONSUMES;
    }
}
