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

import org.deltafi.common.types.EgressFlowPlan;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.ValidateActionConfiguration;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.deltafi.core.plugin.generator.flows.ActionConfigMatchers.ActionConfigMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EgressFlowPlanGeneratorTest {

    EgressFlowPlanGenerator egressFlowPlanGenerator = new EgressFlowPlanGenerator();

    @Test
    void testDefaults() {
        List<FlowPlan> flowPlans = egressFlowPlanGenerator.generateEgressFlowPlans("base", null, null, null);
        assertThat(flowPlans).hasSize(1);
        matches(flowPlans.get(0), "base-egress", ActionConfigMatchers.DEFAULT_FORMAT_MATCHER, ActionConfigMatchers.DEFAULT_EGRESS_MATCHER);
    }

    @Test
    void testExtraFormatAction() {
        List<ActionGeneratorInput> formats = List.of(new ActionGeneratorInput("a1", "org.a1"), new ActionGeneratorInput("a2", "org.a2"), new ActionGeneratorInput("a3", "org.a3"));
        List<ActionGeneratorInput> validates = List.of(new ActionGeneratorInput("v1", "org.v1"));
        List<ActionGeneratorInput> egress = List.of(new ActionGeneratorInput("e1", "org.e1"), new ActionGeneratorInput("e2", "org.e2"));

        List<FlowPlan> flowPlans = egressFlowPlanGenerator.generateEgressFlowPlans("base", formats, validates, egress);
        assertThat(flowPlans).hasSize(3);

        List<ActionConfigMatcher> validateMatchers = List.of(new ActionConfigMatcher("v1", "org.v1"));
        matches(flowPlans.get(0), "base-egress-1", new ActionConfigMatcher("a1", "org.a1"), new ActionConfigMatcher("e1", "org.e1"), validateMatchers);
        matches(flowPlans.get(1), "base-egress-2", new ActionConfigMatcher("a2", "org.a2"), new ActionConfigMatcher("e2", "org.e2"), validateMatchers);
        matches(flowPlans.get(2), "base-egress-3", new ActionConfigMatcher("a3", "org.a3"), new ActionConfigMatcher("e1", "org.e1"), validateMatchers);
    }

    @Test
    void testExtraEgressAction() {
        List<ActionGeneratorInput> formats = List.of(new ActionGeneratorInput("a1", "org.a1"), new ActionGeneratorInput("a2", "org.a2"));
        List<ActionGeneratorInput> egress = List.of(new ActionGeneratorInput("e1", "org.e1"), new ActionGeneratorInput("e2", "org.e2"), new ActionGeneratorInput("e3", "org.e3"));

        List<FlowPlan> flowPlans = egressFlowPlanGenerator.generateEgressFlowPlans("base", formats, null, egress);
        assertThat(flowPlans).hasSize(3);

        matches(flowPlans.get(0), "base-egress-1", new ActionConfigMatcher("a1", "org.a1"), new ActionConfigMatcher("e1", "org.e1"));
        matches(flowPlans.get(1), "base-egress-2", new ActionConfigMatcher("a2", "org.a2"), new ActionConfigMatcher("e2", "org.e2"));
        matches(flowPlans.get(2), "base-egress-3", new ActionConfigMatcher("a1", "org.a1"), new ActionConfigMatcher("e3", "org.e3"));
    }

    @Test
    void testMultipleValidate() {
        List<ActionGeneratorInput> validates = List.of(new ActionGeneratorInput("a1", "org.a1"), new ActionGeneratorInput("a2", "org.a2"));

        List<ActionConfigMatcher> validateMatchers = List.of(new ActionConfigMatcher("a1", "org.a1"), new ActionConfigMatcher("a2", "org.a2"));

        List<FlowPlan> flowPlans = egressFlowPlanGenerator.generateEgressFlowPlans("base", null, validates, null);
        assertThat(flowPlans).hasSize(1);
        matches(flowPlans.get(0), "base-egress", ActionConfigMatchers.DEFAULT_FORMAT_MATCHER, ActionConfigMatchers.DEFAULT_EGRESS_MATCHER, validateMatchers);
    }

    void matches(FlowPlan flowPlan, String name, ActionConfigMatcher formatAction, ActionConfigMatcher egressMatcher) {
        matches(flowPlan, name, formatAction, egressMatcher, null);
    }

    void matches(FlowPlan flowPlan, String name, ActionConfigMatcher formatAction, ActionConfigMatcher egressMatcher, List<ActionConfigMatcher> validateMatchers) {
        if (flowPlan instanceof EgressFlowPlan egressFlowPlan) {
            assertThat(egressFlowPlan.getName()).isEqualTo(name);
            assertThat(egressFlowPlan.getDescription()).isEqualTo("Sample egress flow");
            assertThat(egressFlowPlan.getFormatAction()).matches(formatAction);

            List<ValidateActionConfiguration> validateActions = egressFlowPlan.getValidateActions();
            if (validateMatchers == null) {
                assertThat(validateActions).isNull();
            } else {
                assertThat(validateActions).hasSize(validateMatchers.size());
                validateMatchers.forEach(validateMatcher -> assertThat(validateActions).anyMatch(validateMatcher));
            }

            assertThat(egressFlowPlan.getEgressAction()).matches(egressMatcher);
        } else {
            Assertions.fail("invalid flow type returned");
        }
    }
}