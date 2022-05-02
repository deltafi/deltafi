/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.validation;

import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowErrorType;
import org.deltafi.core.domain.types.Flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class CommonFlowValidator {

    public static List<FlowConfigError> validateConfigurationNames(Flow flow) {
        List<FlowConfigError> errors = new ArrayList<>();

        if (isBlank(flow.getName())) {
            errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                    .configName(flow.getName())
                    .message("The flow name cannot be blank").build());
        }

        errors.addAll(checkForDuplicateActionNames(flow));

        return errors;
    }

    private static List<FlowConfigError> checkForDuplicateActionNames(Flow flow) {
        Map<String, List<String>> nameToActionTypes = new HashMap<>();
        for (ActionConfiguration actionConfiguration : flow.allActionConfigurations()) {
            List<String> actionList = nameToActionTypes.containsKey(actionConfiguration.getName()) ? nameToActionTypes.get(actionConfiguration.getName()) : new ArrayList<>();
            actionList.add(actionConfiguration.getType());
            nameToActionTypes.put(actionConfiguration.getName(), actionList);
        }

        return nameToActionTypes.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> duplicateNameError(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private static FlowConfigError duplicateNameError(String duplicatedName, List<String> actionTypes) {
        return FlowConfigError.newBuilder()
                .configName(duplicatedName)
                .errorType(FlowErrorType.INVALID_CONFIG)
                .message("The action name: " + duplicatedName + " is duplicated for the following action types: " + String.join(", ", actionTypes))
                .build();
    }
}
