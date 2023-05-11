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
import org.deltafi.common.types.TransformActionConfiguration;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.deltafi.core.plugin.generator.flows.ActionConfigMatchers.ActionConfigMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngressFlowPlanGeneratorTest {

    IngressFlowPlanGenerator ingressFlowPlanGenerator = new IngressFlowPlanGenerator();

    @Test
    void testDefaults() {
        List<FlowPlan> flowPlans = ingressFlowPlanGenerator.generateIngressFlowPlans("base", null, null);

        assertThat(flowPlans).hasSize(1);
        matches(flowPlans.get(0), "base-ingress", null, ActionConfigMatchers.DEFAULT_LOAD_MATCHER);
    }

    @Test
    void testMultipleLoadActions() {
        List<ActionGeneratorInput> transforms = List.of(new ActionGeneratorInput("t1", "org.t1"), new ActionGeneratorInput("t2", "org.t2"));
        List<ActionGeneratorInput> loads = List.of(new ActionGeneratorInput("l1", "org.l1"), new ActionGeneratorInput("l2", "org.l2"));
        List<FlowPlan> flowPlans = ingressFlowPlanGenerator.generateIngressFlowPlans("base", transforms, loads);

        assertThat(flowPlans).hasSize(2);
        List<ActionConfigMatcher> transformMatchers = List.of(new ActionConfigMatcher("t1", "org.t1"), new ActionConfigMatcher("t2", "org.t2"));
        matches(flowPlans.get(0), "base-ingress-1", transformMatchers, new ActionConfigMatcher("l1", "org.l1"));
        matches(flowPlans.get(1), "base-ingress-2", transformMatchers, new ActionConfigMatcher("l2", "org.l2"));
    }

    void matches(FlowPlan flowPlan, String name, List<ActionConfigMatcher> transformActionMatchers, ActionConfigMatcher loadActionMatcher) {
        if (flowPlan instanceof IngressFlowPlan ingressFlowPlan) {
            assertThat(ingressFlowPlan.getName()).isEqualTo(name);
            assertThat(ingressFlowPlan.getDescription()).isEqualTo("Sample ingress flow");
            List<TransformActionConfiguration> transformActions = ingressFlowPlan.getTransformActions();
            if (transformActionMatchers == null) {
                assertThat(transformActions).isNull();
            } else {
                assertThat(transformActions).hasSize(transformActionMatchers.size());
                transformActionMatchers.forEach(matcher -> assertThat(transformActions).anyMatch(matcher));
            }
            assertThat(ingressFlowPlan.getLoadAction()).matches(loadActionMatcher);
        } else {
            Assertions.fail("invalid flow type returned");
        }
    }
}