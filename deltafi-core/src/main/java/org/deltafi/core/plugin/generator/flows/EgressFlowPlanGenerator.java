/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.plugin.generator.flows;

import org.deltafi.common.types.EgressActionConfiguration;
import org.deltafi.common.types.EgressFlowPlan;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.FormatActionConfiguration;
import org.deltafi.common.types.ValidateActionConfiguration;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EgressFlowPlanGenerator {

    /**
     * Generate egress flow plans using the given actions
     * @param baseFlowName prefix to use for the flow plan name
     * @param formatActions format actions to use in the flow plans, a default format action will be used if this is null or empty
     * @param validateActions validate actions to use in the flow plans
     * @param egressActions egress actions to use in the flow plans, a default egress action will be used if this is null or empty
     * @return list of flow plans built using the given actions
     */
    List<FlowPlan> generateEgressFlowPlans(String baseFlowName, List<ActionGeneratorInput> formatActions, List<ActionGeneratorInput> validateActions, List<ActionGeneratorInput> egressActions) {
        List<FormatActionConfiguration> formatActionConfigs = ActionUtil.formatActionConfigurations(formatActions);
        List<ValidateActionConfiguration> validateActionConfigs = ActionUtil.validateActionConfigurations(validateActions);
        List<EgressActionConfiguration> egressActionConfigs = ActionUtil.egressActionConfigurations(egressActions);

        String planName = baseFlowName + "-egress";
        List<FlowPlan> egressFlowPlans = new ArrayList<>();

        if (formatActionConfigs.size() == 1 && egressActionConfigs.size() == 1) {
            egressFlowPlans.add(generateEgressFlow(planName, formatActionConfigs.get(0), validateActionConfigs, egressActionConfigs.get(0)));
        } else if (formatActionConfigs.size() >= egressActionConfigs.size()) {
            for (int i = 0; i < formatActionConfigs.size(); i++) {
                FormatActionConfiguration formatActionConfiguration = formatActionConfigs.get(i);
                // reuse the first egress action if the list of egress actions is exhausted before the format action list
                EgressActionConfiguration egressActionConfiguration = i < egressActionConfigs.size() ? egressActionConfigs.get(i) : egressActionConfigs.get(0);
                int planNum = i + 1;
                egressFlowPlans.add(generateEgressFlow(planName + "-" + planNum, formatActionConfiguration, validateActionConfigs, egressActionConfiguration));
            }
        } else {
            for (int i = 0; i < egressActionConfigs.size(); i++) {
                EgressActionConfiguration egressActionConfiguration = egressActionConfigs.get(i);
                // reuse the first format action if the list of format actions is exhausted before the egress action list
                FormatActionConfiguration formatActionConfiguration = i < formatActionConfigs.size() ? formatActionConfigs.get(i) : formatActionConfigs.get(0);
                int planNum = i + 1;
                egressFlowPlans.add(generateEgressFlow(planName + "-" + planNum, formatActionConfiguration, validateActionConfigs, egressActionConfiguration));
            }
        }

        return egressFlowPlans;
    }

    private FlowPlan generateEgressFlow(String planName, FormatActionConfiguration formatAction, List<ValidateActionConfiguration> validateActions, EgressActionConfiguration egressAction) {
        return new EgressFlowPlan(planName, "Sample egress flow", formatAction, validateActions, egressAction);
    }

}
