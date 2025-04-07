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
package org.deltafi.core.action.mediatype;

import org.apache.tika.Tika;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class ModifyMediaType extends TransformAction<ModifyMediaTypeParameters> {
    public ModifyMediaType() {
        super(ActionOptions.builder()
                .description("Modifies content media types.")
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                Input content data is passed through unchanged.
                                
                                If mediaTypeMap is provided, each input content's media type is checked against the keys
                                in this map. Keys may include wildcards (*) to match any sequence of characters. If a
                                match is found, the input content's media type is replaced with the corresponding value.
                                
                                If indexMediaTypeMap is provided, each input content's (zero-based) index is checked
                                against the keys in this map. If a match is found, the input content's media type is
                                replaced with the corresponding value. This map takes precedence over mediaTypeMap.
                                
                                If autodetect is set to true and a media type is not found in mediaTypeMap or
                                indexMediaTypeMap, each input content's media type will be autodetected from its data.
                                If the media type cannot be autodetected, the media type will pass through
                                unchanged.""")
                        .build())
                .errors("On errorOnMissingIndex set to true and content for any index in indexMediaTypeMap is missing")
                .build());
    }

    private static final Tika TIKA = new Tika();

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ModifyMediaTypeParameters params,
            @NotNull TransformInput input) {
        if ((params.getIndexMediaTypeMap() != null) && params.isErrorOnMissingIndex()) {
            List<String> outOfBoundsIndices = params.getIndexMediaTypeMap().keySet().stream()
                    .filter(index -> index < 0 || index >= input.content().size())
                    .map(Object::toString)
                    .toList();
            if (!outOfBoundsIndices.isEmpty()) {
                return new ErrorResult(context, "Indices out of bounds: " + String.join(",", outOfBoundsIndices));
            }
        }

        TransformResult result = new TransformResult(context);
        result.addContent(input.content());

        for (int contentIndex = 0; contentIndex < input.getContent().size(); contentIndex++) {
            ActionContent content = input.content(contentIndex);

            String mediaType = null;

            if (params.getMediaTypeMap() != null) {
                mediaType = params.getMediaTypeMap().entrySet().stream()
                        .filter(entry -> matchesPattern(content.getMediaType(), entry.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
            }

            if (params.getIndexMediaTypeMap() != null) {
                mediaType = params.getIndexMediaTypeMap().getOrDefault(contentIndex, mediaType);
            }

            if ((mediaType == null) && params.isAutodetect()) {
                if (params.isAutodetectByNameOnly()) {
                    mediaType = TIKA.detect(content.getName());
                } else {
                    try {
                        mediaType = TIKA.detect(content.loadInputStream(), content.getName());
                    } catch (IOException ignored) {
                        // do not set the mediaType if there is an exception, but continue processing
                    }
                }
            }

            if (mediaType != null) {
                content.setMediaType(mediaType);
            }
        }

        return result;
    }

    private boolean matchesPattern(final String value, final String pattern) {
        String regexPattern = pattern.replace("*", ".*");
        return value.matches(regexPattern);
    }
}
