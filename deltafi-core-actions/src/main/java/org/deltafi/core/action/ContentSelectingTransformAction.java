/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.action;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;

public abstract class ContentSelectingTransformAction<P extends ContentSelectionParameters> extends TransformAction<P> {
    public ContentSelectingTransformAction(ActionOptions actionOptions) {
        super(actionOptions);
    }

    protected abstract ActionContent transform(ActionContext context, P params, ActionContent content) throws Exception;

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull P params, @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);

        for (int i = 0; i < input.getContent().size(); i++) {
            ActionContent content = input.getContent().get(i);

            if (!params.contentSelected(i, content)) {
                // Pass content through without transforming
                result.addContent(content);
                continue;
            }

            if (params.isRetainExistingContent()) {
                result.addContent(content);
            }

            try {
                result.addContent(transform(context, params, content));
            } catch (Exception e) {
                return new ErrorResult(context, "Error transforming content at index " + i, e);
            }
        }

        return result;
    }
}
