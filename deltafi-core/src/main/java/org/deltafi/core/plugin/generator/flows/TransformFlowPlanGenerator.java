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

import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.TransformActionConfiguration;
import org.deltafi.common.types.TransformFlowPlan;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TransformFlowPlanGenerator {


    /**
     * Create a list of transform flow plans using the given action input
     * @param baseFlowName prefix for the flow plan name
     * @param transformActions list of transform actions to use in the flow plans
     * @return list of transform flow plans
     */
    public List<FlowPlan> generateTransformFlows(String baseFlowName, List<ActionGeneratorInput> transformActions) {
        List<TransformActionConfiguration> transformActionConfigs = ActionUtil.transformActionConfigurations(transformActions);

        List<FlowPlan> flowPlans = new ArrayList<>();

        String planName = baseFlowName + "-transform";
        flowPlans.add(generateTransformFlowPlan(planName, transformActionConfigs));

        return flowPlans;
    }

    private FlowPlan generateTransformFlowPlan(String planName, List<TransformActionConfiguration> transformActions) {
        TransformFlowPlan transformFlowPlan = new TransformFlowPlan(planName, "Sample transform flow");
        transformFlowPlan.setTransformActions(transformActions);
        return transformFlowPlan;
    }
}
