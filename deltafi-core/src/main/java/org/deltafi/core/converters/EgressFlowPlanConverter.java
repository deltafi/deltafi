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
package org.deltafi.core.converters;

import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.EgressActionConfiguration;
import org.deltafi.common.types.FormatActionConfiguration;
import org.deltafi.common.types.ValidateActionConfiguration;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.common.types.EgressFlowPlan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        String replacedName = flowPlanPropertyHelper.getReplacedName(formatActionTemplate);
        List<String> requiresDomains = flowPlanPropertyHelper.replaceListOfPlaceholders(formatActionTemplate.getRequiresDomains(), replacedName);
        FormatActionConfiguration formatActionConfiguration = new FormatActionConfiguration(replacedName, formatActionTemplate.getType(), requiresDomains);
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(formatActionConfiguration, formatActionTemplate);

        List<String> requiresEnrichments = flowPlanPropertyHelper.replaceListOfPlaceholders(formatActionTemplate.getRequiresEnrichments(), formatActionConfiguration.getName());
        formatActionConfiguration.setRequiresEnrichments(requiresEnrichments);

        return formatActionConfiguration;
    }

    List<ValidateActionConfiguration> buildValidateActions(List<ValidateActionConfiguration> validateActionTemplates, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        return null != validateActionTemplates ? validateActionTemplates.stream()
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
        ValidateActionConfiguration validateActionConfiguration = new ValidateActionConfiguration(
                flowPlanPropertyHelper.getReplacedName(validateActionTemplate), validateActionTemplate.getType());
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
        EgressActionConfiguration egressActionConfiguration = new EgressActionConfiguration(
                flowPlanPropertyHelper.getReplacedName(egressActionTemplate), egressActionTemplate.getType());
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(egressActionConfiguration, egressActionTemplate);
        return egressActionConfiguration;
    }

    List<String> buildFlowList(List<String> ingressFlowNames, FlowPlanPropertyHelper flowPlanPropertyHelper, String inEgressPlanNamed) {
        if (null == ingressFlowNames) {
            return null;
        }

        return 1 == ingressFlowNames.size() ?
                handleValue(ingressFlowNames.get(0), flowPlanPropertyHelper, inEgressPlanNamed) :
                handleValues(ingressFlowNames, flowPlanPropertyHelper, inEgressPlanNamed);
    }

    /*
     * If the replacement value is null or blank the entire list will be null
     * If the replacement is wrapped as an array string split it up
     */
    private List<String> handleValue(String inputValue, FlowPlanPropertyHelper flowPlanPropertyHelper, String inEgressPlanNamed) {
        String replacement = flowPlanPropertyHelper.replaceValue(inputValue, inEgressPlanNamed);
        if (StringUtils.isBlank(replacement)) {
            return null;
        } else if (replacement.equals(inputValue)) {
            return List.of(inputValue);
        } else if(FlowPlanPropertyHelper.isArrayString(replacement)) {
            return FlowPlanPropertyHelper.readStringAsList(replacement);
        } else {
            return List.of(replacement);
        }
    }

    // If there were multiple values in the incoming flow plan list then process each one and add the replacement if it is not blank
    private List<String> handleValues(List<String> inputValues, FlowPlanPropertyHelper flowPlanPropertyHelper, String inEgressPlanNamed) {
        Set<String> flowList = new HashSet<>();
        for (String inputValue : inputValues) {
            if (StringUtils.isBlank(inputValue)) {
                continue;
            }

            String replacement = flowPlanPropertyHelper.replaceValue(inputValue, inEgressPlanNamed);
            if (StringUtils.isNotBlank(replacement) && !replacement.equals(inputValue)) {
                if (FlowPlanPropertyHelper.isArrayString(replacement)) {
                    flowList.addAll(FlowPlanPropertyHelper.readStringAsList(replacement));
                } else {
                    flowList.add(replacement);
                }
            } else if (inputValue.equals(replacement)) {
                flowList.add(inputValue);
            }
        }
        return new ArrayList<>(flowList);
    }

    @Override
    EgressFlow getFlowInstance() {
        return new EgressFlow();
    }

}
