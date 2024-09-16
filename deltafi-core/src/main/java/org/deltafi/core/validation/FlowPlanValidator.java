/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.validation;

import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.rules.RuleValidator;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.Publisher;
import org.deltafi.common.types.Subscriber;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.types.FlowPlanEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Verify that there no unrecoverable errors in the FlowPlan.
 */
public abstract class FlowPlanValidator<T extends FlowPlanEntity> {

    private final RuleValidator ruleValidator;

    protected FlowPlanValidator(RuleValidator ruleValidator) {
        this.ruleValidator = ruleValidator;
    }

    /**
     * Check the plan for any configuration errors
     * @param flowPlan plan to valdiate
     */
    public void validate(T flowPlan) {
        List<FlowConfigError> errors = validateConfigurationNames(flowPlan);
        errors.addAll(flowPlanSpecificValidation(flowPlan));
        errors.addAll(validateRules(flowPlan));
        throwIfHasErrors(errors);
    }

    /**
     * Flow plan type specific validation checks
     * @return list of errors
     */
    public List<FlowConfigError> flowPlanSpecificValidation(T flowPlan) {
        return List.of();
    }

    List<FlowConfigError> validateConfigurationNames(T flowPlan) {
        List<FlowConfigError> errors = new ArrayList<>();

        if (isBlank(flowPlan.getName())) {
            errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                    .configName(flowPlan.getName())
                    .message("The flow plan name cannot be blank").build());
        }

        errors.addAll(checkForDuplicateActionNames(flowPlan));

        return errors;
    }

    private List<FlowConfigError> checkForDuplicateActionNames(T flowPlan) {
        Map<String, List<String>> nameToActionTypes = new HashMap<>();
        List<FlowConfigError> errors = new ArrayList<>();
        for (ActionConfiguration actionConfiguration : flowPlan.allActionConfigurations()) {
            if (StringUtils.isBlank(actionConfiguration.getName())) {
                errors.add(FlowConfigError.newBuilder().configName(flowPlan.getName()).message("The plan cannot contain an action configuration with a name that is null or empty").build());
            } else {
                List<String> actionList = nameToActionTypes.containsKey(actionConfiguration.getName()) ? nameToActionTypes.get(actionConfiguration.getName()) : new ArrayList<>();
                actionList.add(actionConfiguration.getType());
                nameToActionTypes.put(actionConfiguration.getName(), actionList);
            }
        }

        nameToActionTypes.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> duplicateNameError(entry.getKey(), entry.getValue()))
                .forEach(errors::add);
        return errors;
    }

    private FlowConfigError duplicateNameError(String duplicatedName, List<String> actionTypes) {
        return FlowConfigError.newBuilder()
                .configName(duplicatedName)
                .errorType(FlowErrorType.INVALID_CONFIG)
                .message("The action name: " + duplicatedName + " is duplicated for the following action types: " + String.join(", ", actionTypes))
                .build();
    }

    private List<FlowConfigError> validateRules(FlowPlanEntity flowPlan) {
        List<FlowConfigError> flowConfigErrors = new ArrayList<>();
        if (flowPlan instanceof Subscriber subscriber) {
            ruleValidator.validateSubscriber(subscriber)
                    .stream().map(message -> toFlowconfigError(flowPlan.getName(), message))
                    .forEach(flowConfigErrors::add);
        }

        if (flowPlan instanceof Publisher publisher) {
            ruleValidator.validatePublisher(publisher)
                    .stream().map(message -> toFlowconfigError(flowPlan.getName(), message))
                    .forEach(flowConfigErrors::add);
        }
        return flowConfigErrors;
    }

    private FlowConfigError toFlowconfigError(String name, String message) {
        return FlowConfigError.newBuilder()
                .configName(name)
                .message(message)
                .errorType(FlowErrorType.INVALID_CONFIG)
                .build();
    }

    void throwIfHasErrors(List<FlowConfigError> errors) {
        if (errors.isEmpty()) {
            return;
        }

        throw new DeltafiConfigurationException(errors.stream().map(this::flowConfigErrorMessage).collect(Collectors.joining("; ")));
    }

    String flowConfigErrorMessage(FlowConfigError flowConfigError) {
        return "Config named: " + flowConfigError.getConfigName() + " had the following error: " + flowConfigError.getMessage();
    }

}
