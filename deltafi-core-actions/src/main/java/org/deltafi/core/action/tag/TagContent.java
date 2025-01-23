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
package org.deltafi.core.action.tag;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class TagContent extends TransformAction<TagContentParameters> {

    public TagContent() {
        super("Adds tags to content based on optional filters such as indices, filename pattern, and media type.");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull TagContentParameters params,
                                         @NotNull TransformInput input) {

        TransformResult result = new TransformResult(context);
        for (int i = 0; i < input.content().size(); i++) {
            ActionContent content = input.content(i);
            result.addContent(content);

            if (!params.contentSelected(i, content)) {
                continue;
            }

            content.addTags(new HashSet<>(params.getTagsToAdd()));
        }

        return result;
    }
}
