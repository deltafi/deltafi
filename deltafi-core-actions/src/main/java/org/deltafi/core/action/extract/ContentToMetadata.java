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
package org.deltafi.core.action.extract;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ContentToMetadata extends TransformAction<ContentToMetadataParameters> {
    public ContentToMetadata() {
        super("Move selected content to metadata or annotation");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ContentToMetadataParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);

        List<String> extractedValues = new ArrayList<>();

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

            extractedValues.add(content.loadString());
        }

        if (!extractedValues.isEmpty()) {
            String value = String.join(params.getMultiValueDelimiter(), extractedValues);
            if (value.length() > params.getMaxSize()) {
                value = value.substring(0, params.getMaxSize());
            }
            if (params.getExtractTarget().equals(ExtractTarget.METADATA)) {
                result.addMetadata(params.getKey(), value);
            } else {
                result.addAnnotation(params.getKey(), value);
            }
        }

        return result;
    }
}
