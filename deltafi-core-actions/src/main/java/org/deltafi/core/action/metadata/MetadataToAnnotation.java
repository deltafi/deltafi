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
package org.deltafi.core.action.metadata;

import org.apache.commons.lang3.StringUtils;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class MetadataToAnnotation extends TransformAction<MetadataToAnnotationParameters> {
    public MetadataToAnnotation() {
        super("Saves metadata as annotations");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull MetadataToAnnotationParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        result.addContent(input.content());
        Map<String, String> filteredMetadata = filterMetadata(input.getMetadata(), params.getMetadataPatterns(), params.getDiscardPrefix());
        result.addAnnotations(filteredMetadata);
        return result;
    }

    private Map<String, String> filterMetadata(Map<String, String> metadata, List<String> patterns, String discardPrefix) {
        if (patterns == null || patterns.isEmpty()) {
            return metadata.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> updateKey(discardPrefix, e.getKey()),
                            e -> e.getValue()));
        }
        return metadata.entrySet().stream()
                .filter(entry -> patterns.stream().anyMatch(pattern -> Pattern.matches(pattern, entry.getKey())))
                .collect(Collectors.toMap(
                        e -> updateKey(discardPrefix, e.getKey()),
                        e -> e.getValue()));
    }

    private String updateKey(String discardPrefix, String key) {
        if (StringUtils.isNoneEmpty(discardPrefix) &&
                key.startsWith(discardPrefix) &&
                key.length() > discardPrefix.length()) {
            return key.replaceFirst(discardPrefix, "");
        }
        return key;
    }
}
