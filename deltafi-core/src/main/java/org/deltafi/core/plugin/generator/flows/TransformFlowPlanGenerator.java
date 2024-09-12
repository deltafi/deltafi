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

import org.deltafi.common.types.*;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class TransformFlowPlanGenerator {

    static final String FLOW_NAME_POSTFIX = "-transform";

    /**
     * Create a list of transform flow plans using the given action input
     * @param baseFlowName prefix for the flow plan name
     * @param transformActions list of transform actions to use in the flow plans
     * @return list of transform flow plans
     */
    public List<FlowPlan> generateTransformFlows(String baseFlowName, List<ActionGeneratorInput> transformActions) {
        List<ActionConfiguration> transformActionConfigs = ActionUtil.transformActionConfigurations(transformActions);
        List<FlowPlan> flowPlans = new ArrayList<>();

        String planName = baseFlowName + FLOW_NAME_POSTFIX;

        Rule subscribeRule = new Rule(baseFlowName + FLOW_NAME_POSTFIX);

        PublishRules publishRules = new PublishRules();
        publishRules.setMatchingPolicy(MatchingPolicy.FIRST_MATCHING);
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.ERROR));
        List<Rule> publishRulesList = List.of(new Rule(baseFlowName + EgressFlowPlanGenerator.FLOW_NAME_POSTFIX));
        publishRules.setRules(publishRulesList);

        flowPlans.add(generateTransformFlowPlan(planName, transformActionConfigs, Set.of(subscribeRule), publishRules));

        return flowPlans;
    }

    private FlowPlan generateTransformFlowPlan(String planName, List<ActionConfiguration> transformActions,
                                               Set<Rule> subscribeRuleSet, PublishRules publishRules) {
        TransformFlowPlan transformFlowPlan = new TransformFlowPlan(planName, "Sample transform flow");
        transformFlowPlan.setTransformActions(transformActions);
        transformFlowPlan.setSubscribe(subscribeRuleSet);
        transformFlowPlan.setPublish(publishRules);

        return transformFlowPlan;
    }
}
