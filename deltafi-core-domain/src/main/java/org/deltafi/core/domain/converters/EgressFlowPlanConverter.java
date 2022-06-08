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

import org.deltafi.core.domain.configuration.EgressActionConfiguration;
import org.deltafi.core.domain.configuration.FormatActionConfiguration;
import org.deltafi.core.domain.configuration.ValidateActionConfiguration;
import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.EgressFlowPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class EgressFlowPlanConverter extends FlowPlanConverter<EgressFlowPlan, EgressFlow> {

    public void populateFlowSpecificFields(EgressFlowPlan egressFlowPlan, EgressFlow egressFlow, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        egressFlow.setFormatAction(buildFormatAction(egressFlowPlan.getFormatAction(), flowPlanPropertyHelper));
        egressFlow.setValidateActions(buildValidateActions(egressFlowPlan.getValidateActions(), flowPlanPropertyHelper));
        egressFlow.setEgressAction(buildEgressAction(egressFlowPlan.getEgressAction(), flowPlanPropertyHelper));

        egressFlow.setIncludeIngressFlows(buildFlowList(egressFlowPlan.getIncludeIngressFlows(), flowPlanPropertyHelper, egressFlow.getName()));
        egressFlow.setExcludeIngressFlows(buildFlowList(egressFlowPlan.getExcludeIngressFlows(), flowPlanPropertyHelper, egressFlow.getName()));
    }

    /**
     * Return a copy of the format action configuration with placeholders resolved where possible.
     *
     * @param formatActionTemplate template of the FormatActionConfiguration that should be created
     * @return FormatActionConfiguration with variable values substituted in
     */
    FormatActionConfiguration buildFormatAction(FormatActionConfiguration formatActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        FormatActionConfiguration formatActionConfiguration = new FormatActionConfiguration();
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(formatActionConfiguration, formatActionTemplate);

        // TODO - should we allow requiresEnrichment and requiresDomain to be replaced?
        List<String> requiresEnrichment = flowPlanPropertyHelper.replaceListOfPlaceholders(formatActionTemplate.getRequiresEnrichment(), formatActionConfiguration.getName());
        List<String> requiresDomains = flowPlanPropertyHelper.replaceListOfPlaceholders(formatActionTemplate.getRequiresDomains(), formatActionConfiguration.getName());

        formatActionConfiguration.setRequiresEnrichment(requiresEnrichment);
        formatActionConfiguration.setRequiresDomains(requiresDomains);

        return formatActionConfiguration;
    }

    List<ValidateActionConfiguration> buildValidateActions(List<ValidateActionConfiguration> validateActionTemplates, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        return Objects.nonNull(validateActionTemplates) ? validateActionTemplates.stream()
                .map(validateTemplate -> buildValidateAction(validateTemplate, flowPlanPropertyHelper))
                .collect(Collectors.toList()) : List.of();
    }

    /**
     * Return a copy of the validate action configuration with placeholders resolved where possible.
     *
     * @param validateActionTemplate template of the ValidateActionConfiguration that should be created
     * @return ValidateActionConfiguration with variable values substituted in
     */
    ValidateActionConfiguration buildValidateAction(ValidateActionConfiguration validateActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
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
    EgressActionConfiguration buildEgressAction(EgressActionConfiguration egressActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {

        EgressActionConfiguration egressActionConfiguration = new EgressActionConfiguration();
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(egressActionConfiguration, egressActionTemplate);

        return egressActionConfiguration;
    }

    List<String> buildFlowList(List<String> fromPlan, FlowPlanPropertyHelper flowPlanPropertyHelper, String flowPlanName) {
        if (null == fromPlan) {
            return null;
        }

        List<String> flowList = new ArrayList<>();
        for (String flow : fromPlan) {
            String replacement = flowPlanPropertyHelper.replaceValue(flow, flowPlanName);
            if (null != replacement && !replacement.equals(flow) && replacement.contains(",")) {
                String[] flows = replacement.split(",");
                for (String split : flows) {
                    flowList.add(split.trim());
                }
            } else {
                flowList.add(replacement);
            }
        }
        return flowList;
    }

    @Override
    EgressFlow getFlowInstance() {
        return new EgressFlow();
    }

}
