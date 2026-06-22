/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.jayway.jsonpath.*;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ExtractJson extends TransformAction<ExtractJsonParameters> {
    private static final Configuration CONFIGURATION = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS, Option.ALWAYS_RETURN_LIST)
            .build();
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public ExtractJson() {
        super(ActionOptions.builder()
                .description("Extracts values from JSON content and writes them to metadata or annotations.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary(ExtractJsonParameters.CONTENT_SELECTION_DESCRIPTION)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .passthrough(true)
                        .metadataSummary("""
                                If extractTarget is METADATA, JSONPath expressions from jsonPathToKeysMap keys are used
                                to extract values from the input content and write them to metadata keys which are the
                                corresponding jsonPathToKeysMap values.
                                
                                Values extracted from multiple contents are handled according to handleMultipleKeys.""")
                        .annotationsSummary("""
                                If extractTarget is ANNOTATIONS, JSONPath expressions from jsonPathToKeysMap keys are
                                used to extract values from the input content and write them to annotation keys which
                                are the corresponding jsonPathToKeysMap values.
                                
                                Values extracted from multiple contents are handled according to handleMultipleKeys.""")
                        .build())
                .errors("On failure to parse any content as JSON and invalidFormatMode set to ERROR",
                        """
                        On errorOnKeyNotFound set to true and no values can be extracted from input content for any
                        jsonPathToKeysMap key""")
                .build());
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ExtractJsonParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);

        Map<String, List<String>> extractedValuesMap = new HashMap<>();
        for (int i = 0; i < input.getContent().size(); i++) {
            ActionContent content = input.content(i);

            if (!params.contentSelected(i, content)) {
                result.addContent(content);
                continue;
            }

            boolean contentAdded = false;
            if (params.isRetainExistingContent()) {
                result.addContent(content);
                contentAdded = true;
            }

            String jsonString = content.loadString();
            if (!isValidRoot(params.isAllowScalarRoot(), jsonString)) {
                if (params.getInvalidFormatMode() == InvalidFormatMode.ERROR) {
                    return new ErrorResult(context, "Unable to read JSON content", "Failed to read " + content.getName());
                } else {
                    if (!contentAdded && params.getInvalidFormatMode() == InvalidFormatMode.PASSTHROUGH) {
                        result.addContent(content);
                    }
                    continue;
                }
            }

            ReadContext ctx;
            try {
                ctx = JsonPath.using(CONFIGURATION).parse(jsonString);
            } catch (Exception e) {
                if (params.getInvalidFormatMode() == InvalidFormatMode.ERROR) {
                    return new ErrorResult(context, "Unable to read JSON content", "Failed to read " + content.getName(), e);
                } else {
                    if (!contentAdded && params.getInvalidFormatMode() == InvalidFormatMode.PASSTHROUGH) {
                        result.addContent(content);
                    }
                    continue;
                }
            }

            for (Map.Entry<String, String> entry : params.getJsonPathToKeysMap().entrySet()) {
                String jsonPath = entry.getKey();
                Object readResult = ctx.read(jsonPath);

                List<String> values = ((List<?>) readResult).stream().filter(Objects::nonNull).map(Object::toString).toList();
                extractedValuesMap.computeIfAbsent(jsonPath, k -> new ArrayList<>()).addAll(values);
            }
        }

        for (Map.Entry<String, String> entry : params.getJsonPathToKeysMap().entrySet()) {
            String jsonPath = entry.getKey();
            String mappedKey = entry.getValue();
            List<String> values = extractedValuesMap.getOrDefault(jsonPath, Collections.emptyList());

            if (values.isEmpty()) {
                if (params.isErrorOnKeyNotFound()) {
                    return new ErrorResult(context, "Key not found: " + jsonPath);
                }
                continue;
            }

            String value = switch (params.getHandleMultipleKeys()) {
                case FIRST -> values.getFirst();
                case LAST -> values.getLast();
                case DISTINCT -> String.join(params.getAllKeysDelimiter(), values.stream().distinct().toList());
                default -> String.join(params.getAllKeysDelimiter(), values);
            };

            if (params.getExtractTarget() == ExtractTarget.METADATA) {
                result.addMetadata(mappedKey, value);
            } else {
                result.addAnnotation(mappedKey, value);
            }
        }

        return result;
    }

    private boolean isValidRoot(boolean allowScalar, String json) {
        JsonNode rootNode = null;
        try {
            rootNode = OBJECT_MAPPER.readTree(json);
        } catch (JacksonException ignored) {
            return false;
        }
        return rootNode != null && (allowScalar || rootNode.isArray() || rootNode.isObject());
    }
}