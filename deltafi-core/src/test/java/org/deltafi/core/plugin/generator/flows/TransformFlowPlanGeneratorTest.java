/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.TransformFlowPlan;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.deltafi.core.plugin.generator.flows.ActionConfigMatchers.ActionConfigMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransformFlowPlanGeneratorTest {

    public static final String MY_PLUGIN_TRANSFORM = "my-plugin-transform";
    TransformFlowPlanGenerator transformFlowPlanGenerator = new TransformFlowPlanGenerator();

    @Test
    void testDefaults() {
        List<FlowPlan> flowPlans = transformFlowPlanGenerator
                .generateTransformFlows("my-plugin", null);
        assertThat(flowPlans).hasSize(1);
        matches(flowPlans.getFirst(), null);
    }

    @Test
    void testMultipleEgressActions() {
        List<ActionGeneratorInput> transforms =
                List.of(new ActionGeneratorInput("t1", "org.t1"),
                        new ActionGeneratorInput("t2", "org.t2"));
        List<FlowPlan> flowPlans =
                transformFlowPlanGenerator.generateTransformFlows("my-plugin", transforms);

        assertThat(flowPlans).hasSize(1);
        List<ActionConfigMatcher> transformMatchers = List.of(new ActionConfigMatcher("t1", "org.t1"),
                new ActionConfigMatcher("t2", "org.t2"));
        matches(flowPlans.getFirst(), transformMatchers);
    }

    void matches(FlowPlan flowPlan, List<ActionConfigMatcher> transformActionMatchers) {
        if (flowPlan instanceof TransformFlowPlan transformFlowPlan) {
            assertThat(transformFlowPlan.getName()).isEqualTo(MY_PLUGIN_TRANSFORM);
            assertThat(transformFlowPlan.getDescription()).isEqualTo("Sample transform flow");
            List<ActionConfiguration> transformActions = transformFlowPlan.getTransformActions();
            if (transformActionMatchers == null) {
                assertThat(transformActions).isNull();
            } else {
                assertThat(transformActions).hasSize(transformActionMatchers.size());
                transformActionMatchers.forEach(matcher -> assertThat(transformActions).anyMatch(matcher));
            }
        } else {
            Assertions.fail("invalid flow type returned");
        }
    }
}