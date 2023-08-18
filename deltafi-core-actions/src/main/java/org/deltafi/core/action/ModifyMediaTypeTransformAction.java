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
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.ModifyMediaTypeParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ModifyMediaTypeTransformAction extends TransformAction<ModifyMediaTypeParameters> {

    public ModifyMediaTypeTransformAction() {
        super("Modify content mediaTypes based on pattern or content index");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull ModifyMediaTypeParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        result.addContent(input.content());

        // Apply media type mappings, supporting wildcards
        for (ActionContent content : result.getContent()) {
            String oldMediaType = content.getMediaType();
            String newMediaType = params.getMediaTypeMap().entrySet().stream()
                    .filter(entry -> matchesPattern(oldMediaType, entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(oldMediaType);

            content.setMediaType(newMediaType);
        }

        // Apply media type changes by index
        for (Map.Entry<Integer, String> entry : params.getIndexMediaTypeMap().entrySet()) {
            int index = entry.getKey();
            if (index < 0 || index >= result.getContent().size()) {
                if (params.isErrorOnMissingIndex()) {
                    return new ErrorResult(context, "Index out of bounds: " + index);
                }
                continue;
            }
            result.getContent().get(index).setMediaType(entry.getValue());
        }

        return result;
    }

    private boolean matchesPattern(final String value, final String pattern) {
        String regexPattern = pattern.replace("*", ".*");
        return value.matches(regexPattern);
    }
}
