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

import com.jayway.jsonpath.*;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.ExtractJsonMetadataParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ExtractJsonMetadataTransformAction extends TransformAction<ExtractJsonMetadataParameters> {
    private static final Configuration CONFIGURATION = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS, Option.ALWAYS_RETURN_LIST)
            .build();

    public ExtractJsonMetadataTransformAction() {
        super("Extract JSON keys based on JSONPath and write them as metadata");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull ExtractJsonMetadataParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        result.addContent(input.content());

        // Define a map to collect the values for each JSONPath expression
        Map<String, List<String>> valuesMap = new HashMap<>();
        List<ActionContent> contentList = params.getContentIndexes() == null ? input.content() : params.getContentIndexes().stream().map(input.content()::get).toList();
        contentList = contentList.stream()
                .filter(c -> params.getMediaTypes().stream()
                        .anyMatch(allowedType -> matchesPattern(c.getMediaType(), allowedType)))
                .filter(c -> params.getFilePatterns() == null || params.getFilePatterns().isEmpty() ||
                        params.getFilePatterns().stream()
                                .anyMatch(pattern -> matchesPattern(c.getName(), pattern)))
                .toList();

        // Iterate through content and extract values using JSONPath
        for (ActionContent content : contentList) {
            String json = content.loadString();

            ReadContext ctx = JsonPath.using(CONFIGURATION).parse(json);
            for (Map.Entry<String, String> entry : params.getJsonPathToMetadataKeysMap().entrySet()) {
                String jsonPath = entry.getKey();
                Object readResult = ctx.read(jsonPath);

                List<String> values = ((List<?>) readResult).stream().filter(Objects::nonNull).map(Object::toString).toList();
                valuesMap.computeIfAbsent(jsonPath, k -> new ArrayList<>()).addAll(values);
            }
        }

        // Handle values according to the specified behavior
        for (Map.Entry<String, String> entry : params.getJsonPathToMetadataKeysMap().entrySet()) {
            String jsonPath = entry.getKey();
            String metadataKey = entry.getValue();
            List<String> values = valuesMap.getOrDefault(jsonPath, Collections.emptyList());

            if (values.isEmpty()) {
                if (params.isErrorOnKeyNotFound()) {
                    return new ErrorResult(context, "Key not found: " + jsonPath);
                } else {
                    continue;
                }
            }

            String value = switch (params.getHandleMultipleKeys()) {
                case FIRST -> values.get(0);
                case LAST -> values.get(values.size() - 1);
                default -> String.join(params.getAllKeysDelimiter(), values);
            };

            result.addMetadata(metadataKey, value);
        }

        return result;
    }

    private boolean matchesPattern(String value, String pattern) {
        return value.matches(pattern.replace("*", ".*"));
    }
}
