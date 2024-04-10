/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.MergeContentParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class MergeContent extends TransformAction<MergeContentParameters> {
    final static String FILENAME_REPLACEMENT = "{{filename}}";

    public MergeContent() {
        super("Merge multiple pieces of content into one blob");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull MergeContentParameters params, @NotNull TransformInput input) {
        ActionContent firstContent = input.content(0);
        ActionContent mergedContent = firstContent.copy();

        String name = params.getMergedFilename();
        if (name == null || name.isEmpty()) {
            name = firstContent.getName();
        }
        mergedContent.setName(name.replace(FILENAME_REPLACEMENT, firstContent.getName()));

        String mediaType = params.getMediaType();
        if (mediaType != null && !mediaType.isEmpty()) {
            mergedContent.setMediaType(mediaType);
        }

        if (input.content().size() > 1) {
            input.content().subList(1, input.content().size()).forEach(mergedContent::append);
        }

        TransformResult result = new TransformResult(context);
        result.addContent(mergedContent);
        return result;
    }
}
