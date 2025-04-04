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
package org.deltafi.core.plugin.generator.flows;

import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.DataSinkPlan;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.Rule;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class DataSinkPlanGenerator {

    static final String FLOW_NAME_POSTFIX = "-data-sink";

    /**
     * Generate dataSink plans using the given actions
     * @param baseFlowName prefix to use for the dataSource plan name
     * @param egressActions egress actions to use in the dataSource plans, a default egress action will be used if this is
     *                      null or empty
     * @return list of dataSource plans built using the given actions
     */
    List<FlowPlan> generateDataSinkPlans(String baseFlowName, List<ActionGeneratorInput> egressActions) {
        List<ActionConfiguration> egressActionConfigs = ActionUtil.egressActionConfigurations(egressActions);

        String planName = baseFlowName + FLOW_NAME_POSTFIX;
        List<FlowPlan> DataSinkPlans = new ArrayList<>();

        if (egressActionConfigs.size() == 1) {
            Rule subscribeRule = new Rule(baseFlowName + FLOW_NAME_POSTFIX);
            DataSinkPlans.add(generateDataSink(planName, egressActionConfigs.getFirst(), Set.of(subscribeRule)));
        } else {
            for (int i = 0; i < egressActionConfigs.size(); i++) {
                int planNum = i + 1;
                Rule subscribeRule = new Rule(baseFlowName + FLOW_NAME_POSTFIX + "-" + planNum);
                ActionConfiguration egressActionConfiguration = egressActionConfigs.get(i);
                DataSinkPlans.add(generateDataSink(planName + "-" + planNum, egressActionConfiguration,
                        Set.of(subscribeRule)));
            }
        }

        return DataSinkPlans;
    }

    private FlowPlan generateDataSink(String planName, ActionConfiguration egressAction,
                                        Set<Rule> subscribeRuleSet) {
        DataSinkPlan DataSinkPlan = new DataSinkPlan(planName, "Sample dataSink", egressAction);
        DataSinkPlan.setSubscribe(subscribeRuleSet);
        return DataSinkPlan;
    }

}
