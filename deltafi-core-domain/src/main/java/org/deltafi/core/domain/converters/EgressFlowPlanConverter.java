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
package org.deltafi.core.domain.converters;

import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.configuration.EgressActionConfiguration;
import org.deltafi.core.domain.configuration.EnrichActionConfiguration;
import org.deltafi.core.domain.configuration.FormatActionConfiguration;
import org.deltafi.core.domain.configuration.ValidateActionConfiguration;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.Variable;
import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.EgressFlowPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EgressFlowPlanConverter {

    /**
     * Convert the given EgressFlowPlan to an EgressFlow using the given variables
     * to resolve any placeholders in the plan.
     * @param egressFlowPlan EgressFlowPlan that will be used to create the egress flow
     * @param variables list of variables that should be used in the EgressFlow
     * @return populated EgressFlow that can be turned on or off if it is valid
     */
    public EgressFlow toEgressFlow(EgressFlowPlan egressFlowPlan, List<Variable> variables) {

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables, egressFlowPlan.getName());

        EgressFlow egressFlow = new EgressFlow();

        egressFlow.setDescription(egressFlowPlan.getDescription());
        egressFlow.setName(egressFlowPlan.getName());
        egressFlow.setSourcePlugin(egressFlowPlan.getSourcePlugin());

        List<EnrichActionConfiguration> enrichActions = new ArrayList<>();
        if (Objects.nonNull(egressFlowPlan.getEnrichActions())) {
            egressFlowPlan.getEnrichActions().stream()
                    .map(enrichActionTemplate -> buildEnrichAction(enrichActionTemplate, flowPlanPropertyHelper))
                    .forEach(enrichActions::add);
        }
        egressFlow.setEnrichActions(enrichActions);
        egressFlow.setFormatAction(buildFormatAction(egressFlowPlan.getFormatAction(), flowPlanPropertyHelper));

        List<ValidateActionConfiguration> validateActions = new ArrayList<>();

        if (Objects.nonNull(egressFlowPlan.getValidateActions())) {
            egressFlowPlan.getValidateActions().stream()
                    .map(validateTemplate -> buildValidateAction(validateTemplate, flowPlanPropertyHelper))
                    .forEach(validateActions::add);
        }

        egressFlow.setValidateActions(validateActions);

        egressFlow.setEgressAction(buildEgressAction(egressFlowPlan.getEgressAction(), flowPlanPropertyHelper));

        egressFlow.setIncludeIngressFlows(flowPlanPropertyHelper.replaceListOfPlaceholders(egressFlowPlan.getIncludeIngressFlows(), egressFlow.getName()));
        egressFlow.setExcludeIngressFlows(flowPlanPropertyHelper.replaceListOfPlaceholders(egressFlowPlan.getExcludeIngressFlows(), egressFlow.getName()));

        List<FlowConfigError> configErrors = new ArrayList<>(flowPlanPropertyHelper.getErrors());

        FlowState state = configErrors.isEmpty() ? FlowState.STOPPED : FlowState.INVALID;
        egressFlow.getFlowStatus().setState(state);
        egressFlow.getFlowStatus().getErrors().addAll(configErrors);

        egressFlow.setVariables(flowPlanPropertyHelper.getAppliedVariables());
        return egressFlow;
    }

    /**
     * Return a copy of the enrich action configuration with placeholders resolved where possible.
     *
     * @param enrichActionTemplate template of the EnrichActionConfiguration that should be created
     * @return EnrichActionConfiguration with variable values substituted in
     */
    EnrichActionConfiguration buildEnrichAction(org.deltafi.core.domain.generated.types.EnrichActionConfiguration enrichActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        EnrichActionConfiguration enrichActionConfiguration = new EnrichActionConfiguration();
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(enrichActionConfiguration, enrichActionTemplate);

        // TODO - should we allow requiresEnrichment and requiresDomain to be replaced?
        List<String> requiresEnrichment = flowPlanPropertyHelper.replaceListOfPlaceholders(enrichActionTemplate.getRequiresEnrichment(), enrichActionConfiguration.getName());
        List<String> requiresDomains = flowPlanPropertyHelper.replaceListOfPlaceholders(enrichActionTemplate.getRequiresDomains(), enrichActionConfiguration.getName());
        List<KeyValue> requiresMetadata = flowPlanPropertyHelper.replaceKeyValuePlaceholders(enrichActionTemplate.getRequiresMetadataKeyValues(), enrichActionConfiguration.getName());

        enrichActionConfiguration.setRequiresEnrichment(requiresEnrichment);
        enrichActionConfiguration.setRequiresDomains(requiresDomains);
        enrichActionConfiguration.setRequiresMetadataKeyValues(requiresMetadata);

        return enrichActionConfiguration;
    }

    /**
     * Return a copy of the format action configuration with placeholders resolved where possible.
     *
     * @param formatActionTemplate template of the FormatActionConfiguration that should be created
     * @return FormatActionConfiguration with variable values substituted in
     */
    FormatActionConfiguration buildFormatAction(org.deltafi.core.domain.generated.types.FormatActionConfiguration formatActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        FormatActionConfiguration formatActionConfiguration = new FormatActionConfiguration();
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(formatActionConfiguration, formatActionTemplate);

        // TODO - should we allow requiresEnrichment and requiresDomain to be replaced?
        List<String> requiresEnrichment = flowPlanPropertyHelper.replaceListOfPlaceholders(formatActionTemplate.getRequiresEnrichment(), formatActionConfiguration.getName());
        List<String> requiresDomains = flowPlanPropertyHelper.replaceListOfPlaceholders(formatActionTemplate.getRequiresDomains(), formatActionConfiguration.getName());

        formatActionConfiguration.setRequiresEnrichment(requiresEnrichment);
        formatActionConfiguration.setRequiresDomains(requiresDomains);

        return formatActionConfiguration;
    }

    /**
     * Return a copy of the validate action configuration with placeholders resolved where possible.
     *
     * @param validateActionTemplate template of the ValidateActionConfiguration that should be created
     * @return ValidateActionConfiguration with variable values substituted in
     */
    ValidateActionConfiguration buildValidateAction(org.deltafi.core.domain.generated.types.ValidateActionConfiguration validateActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        ValidateActionConfiguration validateActionConfiguration = new ValidateActionConfiguration();
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(validateActionConfiguration, validateActionTemplate);

        return validateActionConfiguration;
    }

    /**
     * Return a copy of the egress action configuration with placeholders resolved where possible.
     *
     * @param egressActionTemplate template of the EgressActionConfiguration that should be created
     * @return EgressActionConfiguration with variable values substituted in
     */
    EgressActionConfiguration buildEgressAction(org.deltafi.core.domain.generated.types.EgressActionConfiguration egressActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {

        EgressActionConfiguration egressActionConfiguration = new EgressActionConfiguration();
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(egressActionConfiguration, egressActionTemplate);

        return egressActionConfiguration;
    }

}
