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
package org.deltafi.core.action.annotate;

import org.apache.commons.lang3.StringUtils;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class Annotate extends TransformAction<AnnotateParameters> {
    public Annotate() {
        super(ActionOptions.builder()
                .description("Adds annotations to a DeltaFile.")
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .passthrough(true)
                        .annotationsSummary("""
                                Annotations set in the annotations parameter will be added.
                                
                                If the metadataPatterns parameter is set, metadata matching the key patterns will be
                                added if not already set by the annotations parameter.""")
                        .build())
                .errors("On an annotation parameter that contains a zero-length key or value")
                .build());
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull AnnotateParameters params,
            @NotNull TransformInput input) {
        if (params.getAnnotations() != null) {
            if (params.getAnnotations().keySet().stream().anyMatch(StringUtils::isBlank)) {
                return new ErrorResult(context, "Invalid annotations", "Contains a blank key");
            }
            List<String> blankAnnotations = params.getAnnotations().entrySet().stream()
                    .filter(annotationEntry -> StringUtils.isBlank(annotationEntry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            if (!blankAnnotations.isEmpty()) {
                return new ErrorResult(context, "Invalid annotations",
                        "Annotations with the following keys were invalid: " + String.join(", ", blankAnnotations));
            }
        }

        Map<String, String> annotations = new HashMap<>();

        if (params.getMetadataPatterns() != null) {
            annotations.putAll(input.getMetadata().entrySet().stream()
                    .filter(entry -> params.getMetadataPatterns().stream()
                            .anyMatch(pattern -> entry.getKey().matches(pattern)))
                    .collect(Collectors.toMap(entry -> updateKey(params.getDiscardPrefix(), entry.getKey()),
                            Map.Entry::getValue)));
        }

        if (params.getAnnotations() != null) {
            annotations.putAll(params.getAnnotations());
        }

        TransformResult result = new TransformResult(context);
        result.addContent(input.content());
        result.addAnnotations(annotations);
        return result;
    }

    private String updateKey(String discardPrefix, String key) {
        if (StringUtils.isNotEmpty(discardPrefix) && key.startsWith(discardPrefix) &&
                key.length() > discardPrefix.length()) {
            return key.replaceFirst(discardPrefix, "");
        }
        return key;
    }
}