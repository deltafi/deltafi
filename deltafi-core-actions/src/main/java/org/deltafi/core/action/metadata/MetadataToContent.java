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
package org.deltafi.core.action.metadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MetadataToContent extends TransformAction<MetadataToContentParameters> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public MetadataToContent() {
        super("Converts metadata to JSON content.");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull MetadataToContentParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);

        if (params.isRetainExistingContent()) {
            result.addContent(input.getContent());
        }

        try {
            Map<String, String> filteredMetadata = filterMetadata(input.getMetadata(), params.getMetadataPatterns());
            String jsonMetadata = OBJECT_MAPPER.writeValueAsString(filteredMetadata);
            ActionContent newContent = ActionContent.saveContent(context, jsonMetadata, params.getFilename(), MediaType.APPLICATION_JSON);
            result.addContent(newContent);
        } catch (JsonProcessingException e) {
            return new ErrorResult(context, "Error transforming metadata to content", e);
        }

        return result;
    }

    private Map<String, String> filterMetadata(Map<String, String> metadata, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return metadata;
        }
        return metadata.entrySet().stream()
                .filter(entry -> patterns.stream().anyMatch(pattern -> entry.getKey().matches(pattern)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
