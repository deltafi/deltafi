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
package org.deltafi.core.converters;

import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.common.types.EgressFlowPlan;
import org.deltafi.core.types.EgressFlowPlanEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EgressFlowPlanConverter extends FlowPlanConverter<EgressFlowPlanEntity, EgressFlow> {

    @Override
    public EgressFlow createFlow(EgressFlowPlanEntity egressFlowPlan, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setEgressAction(buildEgressAction(egressFlowPlan.getEgressAction(), flowPlanPropertyHelper));
        egressFlow.setSubscribe(egressFlowPlan.getSubscribe());
        return egressFlow;
    }

    /**
     * Return a copy of the egress action configuration with placeholders resolved where possible.
     *
     * @param egressActionTemplate template of the EgressActionConfiguration that should be created
     * @return EgressActionConfiguration with variable values substituted in
     */
    ActionConfiguration buildEgressAction(ActionConfiguration egressActionTemplate, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        ActionConfiguration egressActionConfiguration = new ActionConfiguration(
                flowPlanPropertyHelper.getReplacedName(egressActionTemplate), ActionType.EGRESS, egressActionTemplate.getType());
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(egressActionConfiguration, egressActionTemplate);
        return egressActionConfiguration;
    }

    List<String> buildFlowList(List<String> ingressFlowNames, FlowPlanPropertyHelper flowPlanPropertyHelper, String inEgressPlanNamed) {
        if (null == ingressFlowNames) {
            return null;
        }

        return 1 == ingressFlowNames.size() ?
                handleValue(ingressFlowNames.getFirst(), flowPlanPropertyHelper, inEgressPlanNamed) :
                handleValues(ingressFlowNames, flowPlanPropertyHelper, inEgressPlanNamed);
    }

    /*
     * If the replacement value is null or blank the entire list will be null
     * If the replacement is wrapped as an array string split it up
     */
    private List<String> handleValue(String inputValue, FlowPlanPropertyHelper flowPlanPropertyHelper, String inEgressPlanNamed) {
        String replacement = flowPlanPropertyHelper.replaceValueAsString(inputValue, inEgressPlanNamed);
        if (StringUtils.isBlank(replacement)) {
            return null;
        } else if (replacement.equals(inputValue)) {
            return List.of(inputValue);
        } else if(isArrayString(replacement)) {
            return VariableDataType.readStringAsList(replacement);
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

            String replacement = flowPlanPropertyHelper.replaceValueAsString(inputValue, inEgressPlanNamed);
            if (StringUtils.isNotBlank(replacement) && !replacement.equals(inputValue)) {
                if (isArrayString(replacement)) {
                    flowList.addAll(VariableDataType.readStringAsList(replacement));
                } else {
                    flowList.add(replacement);
                }
            } else if (inputValue.equals(replacement)) {
                flowList.add(inputValue);
            }
        }
        return new ArrayList<>(flowList);
    }

    private static boolean isArrayString(String value) {
        return value.startsWith("[") && value.endsWith("]");
    }
}
