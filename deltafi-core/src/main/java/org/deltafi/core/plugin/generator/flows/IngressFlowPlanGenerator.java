/**
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

import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.IngressFlowPlan;
import org.deltafi.common.types.LoadActionConfiguration;
import org.deltafi.common.types.TransformActionConfiguration;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class IngressFlowPlanGenerator {

    /**
     * Create a list of ingress flow plans using the given action input
     * @param baseFlowName prefix for the flow plan name
     * @param transformActions list of transform actions to use in the flow plans
     * @param loadActions list of load actions to use in the flow plans, a default load action will be used if this is null or empty
     * @return list of ingress flow plans
     */
    public List<FlowPlan> generateIngressFlowPlans(String baseFlowName, List<ActionGeneratorInput> transformActions, List<ActionGeneratorInput> loadActions) {
        List<TransformActionConfiguration> transformActionConfigs = ActionUtil.transformActionConfigurations(transformActions);
        List<LoadActionConfiguration> loadActionConfigs = ActionUtil.loadActionConfigurations(loadActions);

        String planName = baseFlowName + "-ingress";
        List<FlowPlan> flowPlans = new ArrayList<>();

        if (loadActionConfigs.size() == 1) {
            flowPlans.add(generateIngressFlowPlan(planName, transformActionConfigs, loadActionConfigs.get(0)));
        } else {
            int i = 1;
            for (LoadActionConfiguration loadAction : loadActionConfigs) {
                flowPlans.add(generateIngressFlowPlan(planName + "-" + i++, transformActionConfigs, loadAction));
            }
        }

        return flowPlans;
    }

    private FlowPlan generateIngressFlowPlan(String planName, List<TransformActionConfiguration> transformActionConfigs, LoadActionConfiguration loadAction) {
        IngressFlowPlan ingressFlowPlan = new IngressFlowPlan(planName, "Sample ingress flow");

        ingressFlowPlan.setTransformActions(transformActionConfigs);
        ingressFlowPlan.setLoadAction(loadAction);
        return ingressFlowPlan;
    }
}
