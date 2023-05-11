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

import org.deltafi.common.types.EnrichFlowPlan;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.core.plugin.generator.ActionGeneratorInput;
import org.deltafi.core.plugin.generator.flows.ActionConfigMatchers.ActionConfigMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichFlowPlanGeneratorTest {

    EnrichFlowPlanGenerator enrichFlowPlanGenerator = new EnrichFlowPlanGenerator();

    @Test
    void testDefaults() {
        List<FlowPlan> enrichFlowPlans = enrichFlowPlanGenerator.generateEnrichFlowPlan("base", null, null);
        assertThat(enrichFlowPlans).isEmpty();
    }

    @Test
    void multipleEnrichActions() {
        List<ActionGeneratorInput> enrich = List.of(new ActionGeneratorInput("a1", "org.a1"), new ActionGeneratorInput("a2", "org.a2"));
        List<FlowPlan> enrichFlowPlans = enrichFlowPlanGenerator.generateEnrichFlowPlan("base", null, enrich);

        assertThat(enrichFlowPlans).hasSize(1);

        if (enrichFlowPlans.get(0) instanceof EnrichFlowPlan enrichFlowPlan) {
            assertThat(enrichFlowPlan.getName()).isEqualTo("base-enrich");
            assertThat(enrichFlowPlan.getDescription()).isEqualTo("Sample enrich flow");
            assertThat(enrichFlowPlan.getDomainActions()).isNull();
            assertThat(enrichFlowPlan.getEnrichActions()).hasSize(2).anyMatch(new ActionConfigMatcher("a1", "org.a1")).anyMatch(new ActionConfigMatcher("a2", "org.a2"));
        } else {
            Assertions.fail("invalid flow type returned");
        }
    }

    @Test
    void multipleDomainActions() {
        List<ActionGeneratorInput> domains = List.of(new ActionGeneratorInput("a1", "org.a1"), new ActionGeneratorInput("a2", "org.a2"));
        List<FlowPlan> enrichFlowPlans = enrichFlowPlanGenerator.generateEnrichFlowPlan("base", domains, null);

        assertThat(enrichFlowPlans).hasSize(1);

        if (enrichFlowPlans.get(0) instanceof EnrichFlowPlan enrichFlowPlan) {
            assertThat(enrichFlowPlan.getName()).isEqualTo("base-enrich");
            assertThat(enrichFlowPlan.getDescription()).isEqualTo("Sample enrich flow");
            assertThat(enrichFlowPlan.getDomainActions()).hasSize(2).anyMatch(new ActionConfigMatcher("a1", "org.a1")).anyMatch(new ActionConfigMatcher("a2", "org.a2"));
            assertThat(enrichFlowPlan.getEnrichActions()).isNull();
        } else {
            Assertions.fail("invalid flow type returned");
        }
    }

    @Test
    void multiDomainAndEnrich() {
        List<ActionGeneratorInput> domains = List.of(new ActionGeneratorInput("a1", "org.a1"), new ActionGeneratorInput("a2", "org.a2"));
        List<ActionGeneratorInput> enrich = List.of(new ActionGeneratorInput("e1", "org.e1"), new ActionGeneratorInput("e2", "org.e2"));
        List<FlowPlan> enrichFlowPlans = enrichFlowPlanGenerator.generateEnrichFlowPlan("base", domains, enrich);

        assertThat(enrichFlowPlans).hasSize(1);

        if (enrichFlowPlans.get(0) instanceof EnrichFlowPlan enrichFlowPlan) {
            assertThat(enrichFlowPlan.getName()).isEqualTo("base-enrich");
            assertThat(enrichFlowPlan.getDescription()).isEqualTo("Sample enrich flow");
            assertThat(enrichFlowPlan.getDomainActions()).hasSize(2).anyMatch(new ActionConfigMatcher("a1", "org.a1")).anyMatch(new ActionConfigMatcher("a2", "org.a2"));
            assertThat(enrichFlowPlan.getEnrichActions()).hasSize(2).anyMatch(new ActionConfigMatcher("e1", "org.e1")).anyMatch(new ActionConfigMatcher("e2", "org.e2"));
        } else {
            Assertions.fail("invalid flow type returned");
        }
    }

}