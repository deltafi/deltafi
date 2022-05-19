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

import org.deltafi.core.domain.generated.types.EnrichActionConfiguration;
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
        verifyEnrichActionsPopulated(flowPlan).ifPresent(errors::add);
        errors.addAll(checkForDuplicatesInOtherPlans(flowPlan));
        errors.addAll(findDuplicatesInFlowPlan(flowPlan));

        return errors;
    }

    /**
     * Make sure there are one or more enrich actions in the plan
     * @param flowPlan plan to validate
     * @return optional error if there are no enrich actions
     */
    Optional<FlowConfigError> verifyEnrichActionsPopulated(EnrichFlowPlan flowPlan) {
        if (Objects.isNull(flowPlan.getEnrichActions()) || flowPlan.getEnrichActions().isEmpty()) {
            return Optional.of(FlowConfigError.newBuilder().configName(flowPlan.getName()).message("Enrich flow plans must contain one or more enrich actions").build());
        }

        return Optional.empty();
    }

    /**
     * Find any enrich action types that are entered multiple times in this flow.
     * @param enrichFlowPlan to search for duplicate enrich action types
     * @return list of errors if the same enrich action type is found multiple times
     */
    List<FlowConfigError> findDuplicatesInFlowPlan(EnrichFlowPlan enrichFlowPlan) {
        Map<String, List<EnrichActionConfiguration>> duplicatesInflow = enrichFlowPlan.getEnrichActions().stream()
                .collect(Collectors.groupingBy(EnrichActionConfiguration::getType));

        return duplicatesInflow.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> duplicateEnrichAction(entry, enrichFlowPlan.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Find any enrich action types that are already configured in existing
     * flow plans.
     * @param enrichFlowPlan to search for duplicate enrich action types
     * @return list of errors if new enrich action types already exist in other plans
     */
    List<FlowConfigError> checkForDuplicatesInOtherPlans(EnrichFlowPlan enrichFlowPlan) {
        return enrichFlowPlanService.getAll().stream()
                .filter(otherPlan -> !otherPlan.getName().equals(enrichFlowPlan.getName()))
                .map(otherPlan -> checkForDuplicatesInOtherPlan(enrichFlowPlan, otherPlan))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<FlowConfigError> checkForDuplicatesInOtherPlan(EnrichFlowPlan incomingPlan, EnrichFlowPlan otherPlan) {
        Set<String> existingEnrichActions = otherPlan.enrichActionTypes();
        return incomingPlan.getEnrichActions().stream()
                .map(enrichActionConfiguration -> duplicateActionError(enrichActionConfiguration, existingEnrichActions, incomingPlan.getName(), otherPlan.getName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private FlowConfigError duplicateEnrichAction(Map.Entry<String, List<EnrichActionConfiguration>> entry, String flowName) {
        String actions = entry.getValue().stream().map(EnrichActionConfiguration::getName).collect(Collectors.joining(", "));
        return FlowConfigError.newBuilder().configName(flowName).message("The flow contains the same enrich action type: " + entry.getKey() + " in the following actions: " + actions).build();
    }

    private FlowConfigError duplicateActionError(EnrichActionConfiguration enrichActionConfiguration, Set<String> existingEnrichActions, String planName, String otherPlan) {
        return existingEnrichActions.contains(enrichActionConfiguration.getType()) ?
                FlowConfigError.newBuilder().configName(planName).message("Enrich action of type: " + enrichActionConfiguration.getType() + " is already configured in the enrich flow plan named: " + otherPlan).build() : null;
    }
}
