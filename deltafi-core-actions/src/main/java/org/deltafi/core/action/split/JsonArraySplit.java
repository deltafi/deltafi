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
package org.deltafi.core.action.split;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class JsonArraySplit extends TransformAction<JsonArraySplitParameters> {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public JsonArraySplit() {
        super(ActionOptions.builder()
                .description("Split each object in a JSON Array or line-separated list into a separate content.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary("""
                                Only a single content input is allowed. A JSON Array is expected,
                                but a single JSON object per line of content is also supported.
                                Other types can be a pass through or error depending on parameter values.
                                """)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                Each JSON object from the input JSON Array (or line) will be saved to its own content. The name of the new
                                content will remain the same. However, the 'contentSuffix' parameter can be used
                                to append a suffix to the new content name, which will be followed by a 1-up index.
                                E.g. if the input was 'name.json' and the contentSuffix is '-', new names will be
                                'name.json-0', 'name.json-1', etc. Formatting of output JSON may differ from the
                                original. When the input is not a JSON Array, pass through parameters can be used
                                to treat the condition as an error, or pass the data through unchanged.
                                """)
                        .build())
                .errors("Not a JSON object or array",
                        "Could not parse JSON content",
                        "JSON parse error on line #",
                        "JSON object not found on line #",
                        "Content is not a JSON array")
                .notes(" Textual JSON which is neither an object nor an array will not be recognized as JSON.")
                .build());
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull JsonArraySplitParameters params,
                                         @NotNull TransformInput input) {

        if (input.getContent().size() != 1) {
            return new ErrorResult(context, "Expected one content but received "
                    + input.getContent().size());
        }

        ActionContent firstContent = input.getContent().getFirst();
        TransformResult result = new TransformResult(context);

        JsonNode rootNode = null;
        try {
            rootNode = OBJECT_MAPPER.readTree(firstContent.loadString());
        } catch (JacksonException ignored) {
        }

        if (rootNode == null || !(rootNode.isArray() || rootNode.isObject())) {
            if (params.isPassThroughNonJson()) {
                result.addContent(firstContent);
                return result;
            } else {
                if (rootNode == null) {
                    return new ErrorResult(context, "Could not parse JSON content");
                } else {
                    return new ErrorResult(context, "Not a JSON object or array");
                }
            }
        }

        // JSON Array or content starts with a JSON object
        if (rootNode.isArray()) {
            return splitArray(context, params, firstContent, rootNode);
        } else if (!params.isPassThroughNonJsonArray()) {
            return new ErrorResult(context, "Content is not a JSON array");
        }

        // content starts with a JSON object
        if (params.isSplitOneObjectPerLine()) {
            List<JsonNode> nodesByLine = new ArrayList<>();
            String error = parseLinesAsObjects(firstContent, nodesByLine);
            if (error != null) {
                return new ErrorResult(context, error);
            }
            if (nodesByLine.size() > 1) {
                int index = 0;
                for (JsonNode jsonNode : nodesByLine) {
                    try {
                        writeNode(params, jsonNode, firstContent, index, result);
                        ++index;
                    } catch (JacksonException e) {
                        return new ErrorResult(context, "JSON write error", e);
                    }
                }
            }
        }

        if (result.getContent().isEmpty()) {
            // Pass through the json unchanged
            result.addContent(firstContent);
        }
        return result;
    }

    private TransformResultType splitArray(ActionContext context, JsonArraySplitParameters params,
                                           ActionContent content, JsonNode rootNode) {
        TransformResult result = new TransformResult(context);
        int index = 0;
        for (JsonNode jsonNode : rootNode) {
            try {
                writeNode(params, jsonNode, content, index, result);
                ++index;
            } catch (JacksonException e) {
                return new ErrorResult(context, "JSON write error", e);
            }
        }
        return result;
    }

    private String parseLinesAsObjects(ActionContent content, List<JsonNode> nodes) {
        try (InputStream inputStream = content.loadInputStream()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                int lineNum = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    ++lineNum;
                    if (!line.trim().isEmpty()) {
                        try {
                            JsonNode lineNode = OBJECT_MAPPER.readTree(line);
                            if (!lineNode.isObject()) {
                                return "JSON object not found on line " + lineNum;
                            }
                            nodes.add(lineNode);
                        } catch (JacksonException e) {
                            if (nodes.isEmpty()) {
                                // Content is likely valid json, but not 1 object per line
                                return null;
                            } else {
                                return "JSON parse error on line " + lineNum;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private void writeNode(JsonArraySplitParameters params,
                           JsonNode jsonNode, ActionContent content,
                           int index, TransformResult result) throws JacksonException {
        String contentName = content.getName();
        if (params.getContentSuffix() != null) {
            contentName += params.getContentSuffix() + index;
        }

        result.saveContent(
                OBJECT_MAPPER.writeValueAsString(jsonNode),
                contentName, MediaType.APPLICATION_JSON);
    }
}
