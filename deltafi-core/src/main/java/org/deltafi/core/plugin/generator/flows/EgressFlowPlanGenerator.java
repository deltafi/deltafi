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
import org.deltafi.common.types.Rule;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class EgressFlowPlanGenerator {

    static final String FLOW_NAME_POSTFIX = "-egress";

    /**
     * Generate egress flow plans using the given actions
     * @param baseFlowName prefix to use for the flow plan name
     * @param egressActions egress actions to use in the flow plans, a default egress action will be used if this is
     *                      null or empty
     * @return list of flow plans built using the given actions
     */
    List<FlowPlan> generateEgressFlowPlans(String baseFlowName, List<ActionGeneratorInput> egressActions) {
        List<EgressActionConfiguration> egressActionConfigs = ActionUtil.egressActionConfigurations(egressActions);

        String planName = baseFlowName + FLOW_NAME_POSTFIX;
        List<FlowPlan> egressFlowPlans = new ArrayList<>();

        if (egressActionConfigs.size() == 1) {
            Rule subscribeRule = new Rule(baseFlowName + FLOW_NAME_POSTFIX);
            egressFlowPlans.add(generateEgressFlow(planName, egressActionConfigs.getFirst(), Set.of(subscribeRule)));
        } else {
            for (int i = 0; i < egressActionConfigs.size(); i++) {
                int planNum = i + 1;
                Rule subscribeRule = new Rule(baseFlowName + FLOW_NAME_POSTFIX + "-" + planNum);
                EgressActionConfiguration egressActionConfiguration = egressActionConfigs.get(i);
                egressFlowPlans.add(generateEgressFlow(planName + "-" + planNum, egressActionConfiguration,
                        Set.of(subscribeRule)));
            }
        }

        return egressFlowPlans;
    }

    private FlowPlan generateEgressFlow(String planName, EgressActionConfiguration egressAction,
                                        Set<Rule> subscribeRuleSet) {
        EgressFlowPlan egressFlowPlan = new EgressFlowPlan(planName, "Sample egress flow", egressAction);
        egressFlowPlan.setSubscribe(subscribeRuleSet);
        return egressFlowPlan;
    }

}
