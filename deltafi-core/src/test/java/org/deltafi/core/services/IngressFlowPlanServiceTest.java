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
package org.deltafi.core.services;

import org.deltafi.common.types.IngressFlowPlan;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.deltafi.core.repo.IngressFlowPlanRepo;
import org.deltafi.core.util.FlowBuilders;
import org.deltafi.core.validation.IngressFlowPlanValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class IngressFlowPlanServiceTest {

    private static final PluginCoordinates PLUGIN_COORDINATES = new PluginCoordinates("group", "artId", "1.0.0");
    private static final IngressFlowPlan PLAN_A = FlowBuilders.buildIngressFlowPlan("a", PLUGIN_COORDINATES);
    private static final IngressFlowPlan PLAN_B = FlowBuilders.buildIngressFlowPlan("b", PLUGIN_COORDINATES);
    private static final IngressFlowPlan PLAN_C = FlowBuilders.buildIngressFlowPlan("c", PLUGIN_COORDINATES);

    @InjectMocks
    IngressFlowPlanService ingressFlowPlanService;

    @Mock
    IngressFlowPlanValidator ingressFlowPlanValidator;
    @Mock
    IngressFlowPlanRepo flowPlanRepo;
    @Mock
    private IngressFlowService flowService;

    @Test
    void testUpgradeFlowPlans() {
        Mockito.when(flowPlanRepo.findByGroupIdAndArtifactId("group", "artId"))
                .thenReturn(List.of(PLAN_A, PLAN_B, PLAN_C));

        List<IngressFlowPlan> plans = List.of(PLAN_A, PLAN_B);
        ingressFlowPlanService.upgradeFlowPlans(PLUGIN_COORDINATES, plans);

        Mockito.verify(flowPlanRepo).saveAll(plans);
        Mockito.verify(flowPlanRepo).deleteAllById(Set.of("c"));
        Mockito.verify(flowService).upgradeFlows(PLUGIN_COORDINATES, plans, Set.of("a", "b"));
    }

    @Test
    void testValidateFlowPlans() {
        PluginCoordinates otherPlugin = new PluginCoordinates("group2", "art2", "1.0.0");
        IngressFlowPlan otherB = FlowBuilders.buildIngressFlowPlan("b", otherPlugin);
        List<IngressFlowPlan> plans = List.of(PLAN_A, PLAN_B, PLAN_C);

        Mockito.lenient().when(flowPlanRepo.findById("b")).thenReturn(Optional.of(otherB));
        Mockito.lenient().doThrow(new DeltafiConfigurationException("bad config")).when(ingressFlowPlanValidator).validate(PLAN_C);

        List<String> errors = ingressFlowPlanService.validateFlowPlans(plans);

        assertThat(errors).hasSize(2).contains("bad config",
                "A flow plan with the name: b already exists from another source plugin: " + otherPlugin);
    }

}