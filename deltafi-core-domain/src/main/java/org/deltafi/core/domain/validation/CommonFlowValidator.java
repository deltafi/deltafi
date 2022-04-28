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
