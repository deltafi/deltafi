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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
public class JsonArraySplit extends TransformAction<JsonArraySplitParameters> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public JsonArraySplit() {
        super(ActionOptions.builder()
                .description("Split each object in a JSON Array into a separate content.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary("""
                                Only a single content input is allowed. A JSON Array is expected
                                but other types can be a pass through or error depending on parameter values.
                                """)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                Each JSON object from the input JSON Array will be saved to its own content. The name of the new
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

        TransformResult result = new TransformResult(context);

        JsonNode rootNode = null;
        try {
            rootNode = OBJECT_MAPPER.readTree(input.getContent().getFirst().loadString());
            if (!params.isPassThroughNonJson() && !rootNode.isArray() && !rootNode.isObject()) {
                return new ErrorResult(context, "Not a JSON object or array");
            }

        } catch (JsonProcessingException e) {
            if (!params.isPassThroughNonJson()) {
                return new ErrorResult(context, "Could not parse JSON content", e.getMessage());
            }
        }

        if (rootNode != null) {
            if (rootNode.isArray()) {
                int index = 0;
                for (JsonNode jsonNode : rootNode) {
                    String contentName = input.getContent().getFirst().getName();
                    if (params.getContentSuffix() != null) {
                        contentName += params.getContentSuffix() + index;
                    }

                    try {
                        result.saveContent(
                                OBJECT_MAPPER.writeValueAsString(jsonNode),
                                contentName, MediaType.APPLICATION_JSON);
                    } catch (JsonProcessingException e) {
                        return new ErrorResult(context, "JSON write failure", e.getMessage());
                    }
                    ++index;
                }
            } else if (!params.isPassThroughNonJsonArray()) {
                return new ErrorResult(context, "Content is not a JSON array");
            }
        }
        if (result.getContent().isEmpty()) {
            // Non-error cases are pass-through
            result.addContent(input.getContent());
        }
        return result;
    }
}
