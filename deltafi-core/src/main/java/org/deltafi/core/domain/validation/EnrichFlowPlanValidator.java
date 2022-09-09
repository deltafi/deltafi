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
import org.deltafi.core.domain.configuration.ActionRequiringDomainConfiguration;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.services.EnrichFlowPlanService;
import org.deltafi.core.domain.types.EnrichFlowPlan;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EnrichFlowPlanValidator extends FlowPlanValidator<EnrichFlowPlan> {

    private final EnrichFlowPlanService enrichFlowPlanService;

    public EnrichFlowPlanValidator(@Lazy EnrichFlowPlanService enrichFlowPlanService) {
        this.enrichFlowPlanService = enrichFlowPlanService;
    }

    @Override
    public List<FlowConfigError> flowPlanSpecificValidation(EnrichFlowPlan flowPlan) {
        List<FlowConfigError> errors = new ArrayList<>();

        if (blankList(flowPlan.getDomainActions()) && blankList(flowPlan.getEnrichActions())) {
            errors.add(FlowConfigError.newBuilder().configName(flowPlan.getName()).message("Enrich flow plans must contain one or more domain and/or enrich actions").build());
        }

        errors.addAll(checkForDuplicatesInOtherPlans(flowPlan));
        errors.addAll(findDuplicatesInFlowPlan(flowPlan));

        return errors.stream().distinct().collect(Collectors.toList());
    }

    List<FlowConfigError> findDuplicatesInFlowPlan(EnrichFlowPlan flowPlan) {
        List<FlowConfigError> errors = new ArrayList<>();
        errors.addAll(findDuplicateActionTypes(flowPlan.getEnrichActions(), flowPlan.getName()));
        errors.addAll(findDuplicateActionTypes(flowPlan.getDomainActions(), flowPlan.getName()));
        return errors.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Check the given action list for duplicate action types that contain at least one of the same required domains
     * @param actions list of actions to check
     * @param flowPlanName name used in the error message if duplicates exist
     * @return list of errors if duplicate action types are found
     */
    List<FlowConfigError> findDuplicateActionTypes(List<? extends ActionRequiringDomainConfiguration> actions, String flowPlanName) {
        if (null == actions || actions.size() <= 1) {
            return Collections.emptyList();
        }

        List<FlowConfigError> errors = new ArrayList<>();
        List<? extends ActionRequiringDomainConfiguration> compareActions = new ArrayList<>(actions);
        ActionRequiringDomainConfiguration action = compareActions.remove(0);
        while (!compareActions.isEmpty()) {
            errors.addAll(checkForDuplicatesInOtherPlan(List.of(action), compareActions, flowPlanName, flowPlanName));
            action = compareActions.remove(0);
        }

        return errors.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Find any action types that are already configured in an existing
     * flow plan.
     * @param enrichFlowPlan to search for duplicate enrich action types that contain at least one of the same required domains
     * @return list of errors if new enrich action types already exist in other plans
     */
    List<FlowConfigError> checkForDuplicatesInOtherPlans(EnrichFlowPlan enrichFlowPlan) {
        return enrichFlowPlanService.getAll().stream()
                .filter(otherPlan -> !otherPlan.getName().equals(enrichFlowPlan.getName()))
                .map(otherPlan -> checkForDuplicatesInOtherPlan(enrichFlowPlan, otherPlan))
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<FlowConfigError> checkForDuplicatesInOtherPlan(EnrichFlowPlan incomingPlan, EnrichFlowPlan otherPlan) {
        List<FlowConfigError> errors = new ArrayList<>();
        errors.addAll(checkForDuplicatesInOtherPlan(incomingPlan.getDomainActions(), otherPlan.getDomainActions(), incomingPlan.getName(), otherPlan.getName()));
        errors.addAll(checkForDuplicatesInOtherPlan(incomingPlan.getEnrichActions(), otherPlan.getEnrichActions(), incomingPlan.getName(), otherPlan.getName()));
        return errors.stream().distinct().collect(Collectors.toList());
    }

    private List<FlowConfigError> checkForDuplicatesInOtherPlan(List<? extends ActionRequiringDomainConfiguration> incomingActions, List<? extends ActionRequiringDomainConfiguration> existingActions, String incomingPlanName, String otherPlanName) {
        return incomingActions != null ? findDuplicateActionConfig(incomingActions, existingActions).stream()
                .map(dupeAction -> duplicateActionError(dupeAction, incomingPlanName, otherPlanName))
                .distinct()
                .collect(Collectors.toList()) : Collections.emptyList();
    }

    private List<ActionRequiringDomainConfiguration> findDuplicateActionConfig(List<? extends ActionRequiringDomainConfiguration> incomingActions, List<? extends ActionRequiringDomainConfiguration> existingActions) {
        return incomingActions.stream()
                .filter(action -> existingActions.stream()
                        .anyMatch(existingAction -> existingAction.getType().equals(action.getType())
                                && existingAction.getRequiresDomains().stream().anyMatch(domain -> action.getRequiresDomains().contains(domain))))
                .collect(Collectors.toList());
    }

    private FlowConfigError duplicateActionError(ActionRequiringDomainConfiguration actionConfiguration, String planName, String otherPlan) {
        return FlowConfigError.newBuilder().configName(planName).message("Action of type: " + actionConfiguration.getType() +
                " is already configured in the enrich flow plan named: " + otherPlan +
                " with overlapping domain" +
                (actionConfiguration.getRequiresDomains().size() > 1 ? "s: at least one of " : ": ")
                + String.join(", ", actionConfiguration.getRequiresDomains())).build();
    }

    private boolean blankList(List<? extends ActionConfiguration> actions) {
        return null == actions || actions.isEmpty();
    }
}
