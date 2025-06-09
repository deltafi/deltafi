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

import org.apache.commons.lang3.StringUtils;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ValidateMetadata extends TransformAction<ValidateMetadataParameters> {

    public ValidateMetadata() {
        super(ActionOptions.builder()
                .description("Validates metadata.")
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .passthrough(true)
                        .metadataSummary("""
                                Required metadata is validated.
                                
                                The requiredMetadata map is used to specify metadata keys that are required from the
                                input. Within the map, a required metadata key may optional specify a RegEx pattern the
                                key's value must match. An empty string ("") or RegEx pattern of ".*" may be used of the
                                key's value can be of any value pattern.""")
                        .build())
                .build());
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull ValidateMetadataParameters params,
                                         @NotNull TransformInput input) {
        if (params.getRequiredMetadata() == null || params.getRequiredMetadata().isEmpty()) {
            return new TransformResult(context, input.getContent());
        }
        Set<String> missingKeys = new HashSet<>();
        Map<String, BadValue> invalidValues = new HashMap<>();

        for (Map.Entry<String, String> entry : params.getRequiredMetadata().entrySet()) {
            String value = input.metadata(entry.getKey(), "");
            if (StringUtils.isEmpty(value)) {
                missingKeys.add(entry.getKey());
            } else {
                String pattern = entry.getValue();
                if (StringUtils.isNotEmpty(pattern) && !Pattern.matches(pattern, value)) {
                    invalidValues.put(entry.getKey(), new BadValue(value, pattern));
                }
            }
        }

        if (!missingKeys.isEmpty() || !invalidValues.isEmpty()) {
            return new ErrorResult(context, "Required metadata is missing or invalid",
                    errorDetails(missingKeys, invalidValues));
        }

        return new TransformResult(context, input.getContent());
    }

    private String errorDetails(Set<String> missingKeys, Map<String, BadValue> invalidValues) {
        StringBuilder sb = new StringBuilder();
        if (!missingKeys.isEmpty()) {
            sb.append("Missing required keys: ").append(String.join(", ", missingKeys.stream().sorted().toList())).append("\n");
        }
        if (!invalidValues.isEmpty()) {
            sb.append("Invalid metadata:");
            for (Map.Entry<String, BadValue> entry : invalidValues.entrySet()) {
                sb.append("\n- ").append(entry.getKey()).append(": ").append(entry.getValue().value()).append(" (does not match ").append(entry.getValue().pattern()).append(")");
            }
        }
        return sb.toString();
    }

    protected record BadValue(String value, String pattern) {
    }
}
