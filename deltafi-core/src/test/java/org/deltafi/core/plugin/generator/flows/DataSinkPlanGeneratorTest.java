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

import org.deltafi.common.types.DataSinkPlan;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.deltafi.core.plugin.generator.flows.ActionConfigMatchers.ActionConfigMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataSinkPlanGeneratorTest {

    DataSinkPlanGenerator DataSinkPlanGenerator = new DataSinkPlanGenerator();

    @Test
    void testDefaults() {
        List<FlowPlan> flowPlans = DataSinkPlanGenerator.generateDataSinkPlans("base", null);
        assertThat(flowPlans).hasSize(1);
        matches(flowPlans.getFirst(), "base-data-sink", ActionConfigMatchers.DEFAULT_EGRESS_MATCHER);
    }

    @Test
    void testExtraFormatAction() {
        List<ActionGeneratorInput> egress = List.of(new ActionGeneratorInput("e1", "org.e1"),
                new ActionGeneratorInput("e2", "org.e2"));

        List<FlowPlan> flowPlans = DataSinkPlanGenerator.generateDataSinkPlans("base", egress);
        assertThat(flowPlans).hasSize(2);

        matches(flowPlans.get(0), "base-data-sink-1", new ActionConfigMatcher("e1", "org.e1"));
        matches(flowPlans.get(1), "base-data-sink-2", new ActionConfigMatcher("e2", "org.e2"));
    }

    @Test
    void testExtraEgressAction() {
        List<ActionGeneratorInput> egress = List.of(new ActionGeneratorInput("e1", "org.e1"),
                new ActionGeneratorInput("e2", "org.e2"),
                new ActionGeneratorInput("e3", "org.e3"));

        List<FlowPlan> flowPlans = DataSinkPlanGenerator.generateDataSinkPlans("base", egress);
        assertThat(flowPlans).hasSize(3);

        matches(flowPlans.get(0), "base-data-sink-1", new ActionConfigMatcher("e1", "org.e1"));
        matches(flowPlans.get(1), "base-data-sink-2", new ActionConfigMatcher("e2", "org.e2"));
        matches(flowPlans.get(2), "base-data-sink-3", new ActionConfigMatcher("e3", "org.e3"));
    }

    @Test
    void testMultipleValidate() {
        List<FlowPlan> flowPlans = DataSinkPlanGenerator.generateDataSinkPlans("base", null);
        assertThat(flowPlans).hasSize(1);
        matches(flowPlans.getFirst(), "base-data-sink", ActionConfigMatchers.DEFAULT_EGRESS_MATCHER);
    }

    void matches(FlowPlan flowPlan, String name, ActionConfigMatcher egressMatcher) {
        if (flowPlan instanceof DataSinkPlan DataSinkPlan) {
            assertThat(DataSinkPlan.getName()).isEqualTo(name);
            assertThat(DataSinkPlan.getDescription()).isEqualTo("Sample dataSink");

            assertThat(DataSinkPlan.getEgressAction()).matches(egressMatcher);
        } else {
            Assertions.fail("invalid dataSink type returned");
        }
    }
}