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
package org.deltafi.core.services;

import lombok.Builder;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.types.*;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.collect.CollectEntry;
import org.deltafi.core.collect.CollectEntryService;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.services.pubsub.PublisherService;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.StateMachineInput;
import org.deltafi.core.types.TransformFlow;
import org.deltafi.core.util.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class StateMachineTest {

    private static final String EGRESS_ACTION = "TheEgressAction";
    
    private static final String TRANSFORM_FLOW = "TheTransformFlow";
    private static final String EGRESS_FLOW = "TheEgressFlow";

    @Mock
    private TransformFlowService transformFlowService;
    @Mock
    private EgressFlowService egressFlowService;
    @Mock
    private QueueManagementService queueManagementService;
    @Mock
    private CollectEntryService collectEntryService;
    @Mock
    private PublisherService publisherService;
    @Mock
    private AnalyticEventService analyticEventService;
    @Spy
    private Clock clock = new TestClock();
    @Spy
    private DeltaFiPropertiesService deltaFiPropertiesService = new MockDeltaFiPropertiesService();

    @InjectMocks
    private StateMachine stateMachine;

    @BeforeEach
    void setup() {
        Mockito.lenient().when(queueManagementService.coldQueue(anyString(), anyLong())).thenReturn(false);
    }

    @Test
    void testAdvanceToEgressActionWhenInTransformTestMode() {
        DeltaFile deltaFile = Util.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);

        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();
        deltaFileFlow.setType(FlowType.TRANSFORM);

        TransformFlow transformFlow = TransformFlowMaker.builder()
                .name(TRANSFORM_FLOW)
                .testMode(true)
                .flowState(FlowState.RUNNING).build().makeTransformFlow();
        Mockito.when(transformFlowService.getRunningFlowByName(TRANSFORM_FLOW)).thenReturn(transformFlow);

        EgressFlow egressFlowConfig = EgressFlowMaker.builder().build().makeEgressFlow();
        Mockito.when(egressFlowService.getRunningFlowByName("egressFlow"))
                .thenReturn(egressFlowConfig);

        DeltaFileFlow egressFlow = new DeltaFileFlow();
        egressFlow.setName("egressFlow");
        egressFlow.setType(FlowType.EGRESS);
        egressFlow.setTestMode(true);
        egressFlow.setTestModeReason("test mode reason");
        egressFlow.setActionConfigurations(new ArrayList<>(egressFlowConfig.allActionConfigurations()));
        Mockito.when(publisherService.subscribers(transformFlow, deltaFile, deltaFileFlow))
                .thenReturn(Set.of(egressFlow));

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileFlow);
        List<ActionInput> actionInputs = stateMachine.advance(List.of(stateMachineInput));
        assertThat(actionInputs).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
        assertThat(deltaFile.getFiltered()).isTrue();
        assertThat(egressFlow.getActions()).hasSize(1);
        Action egressAction = egressFlow.getActions().getFirst();
        assertThat(egressAction.getName()).isEqualTo(SYNTHETIC_EGRESS_ACTION_FOR_TEST);
        assertThat(egressAction.getState()).isEqualTo(ActionState.FILTERED);
        assertThat(egressAction.getFilteredCause()).isEqualTo("Filtered by test mode");
        assertThat(egressAction.getFilteredContext()).isEqualTo("Filtered by test mode with a reason of - test mode reason");
    }

    @Test
    void advancesInTransformationFlowWithCollectingTransformAction() {
        DeltaFile deltaFile = Util.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);

        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();
        deltaFileFlow.setType(FlowType.TRANSFORM);
        TransformActionConfiguration transformAction = new TransformActionConfiguration("CollectingTransformAction",
                "org.deltafi.action.SomeCollectingTransformAction");
        transformAction.setCollect(new CollectConfiguration(Duration.parse("PT1S"), null, 3, null));
        // add the transform action as the next action config to use in the DeltaFileFlow
        deltaFileFlow.setActionConfigurations(new ArrayList<>(List.of(transformAction)));

        CollectEntry collectEntry = new CollectEntry();
        collectEntry.setCount(2);
        Mockito.when(collectEntryService.upsertAndLock(Mockito.any(), Mockito.any(), Mockito.isNull(), Mockito.eq(3),
                Mockito.eq(0), Mockito.eq(deltaFile.getDid()))).thenReturn(collectEntry);

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileFlow);
        List<ActionInput> actionInvocations = stateMachine.advance(List.of(stateMachineInput));

        assertTrue(actionInvocations.isEmpty());
        List<Action> collectingActions = deltaFileFlow.getActions().stream()
                .filter(action -> action.getState().equals(ActionState.COLLECTING)).toList();
        assertEquals(1, collectingActions.size());
        Action collectingAction = collectingActions.getFirst();
        assertEquals("CollectingTransformAction", collectingAction.getName());
        assertEquals(ActionType.TRANSFORM, collectingAction.getType());
    }

    @Builder
    private static class TransformFlowMaker {
        @Builder.Default
        final String name = TRANSFORM_FLOW;
        @Builder.Default
        final FlowState flowState = FlowState.STOPPED;
        @Builder.Default
        final boolean testMode = false;

        private TransformFlow makeTransformFlow() {
            TransformFlow transformFlow = new TransformFlow();
            transformFlow.setName(name);
            transformFlow.setFlowStatus(FlowStatus.newBuilder().state(flowState).testMode(testMode).build());

            return transformFlow;
        }

    }

    @Builder
    private static class EgressFlowMaker {
        @Builder.Default
        final String egressActionName = EGRESS_ACTION;
        @Builder.Default
        final String name = EGRESS_FLOW;
        @Builder.Default
        final FlowState flowState = FlowState.STOPPED;
        @Builder.Default
        final boolean testMode = false;

        private EgressFlow makeEgressFlow() {
            EgressFlow egressFlow = new EgressFlow();
            egressFlow.setName(name);
            EgressActionConfiguration egressActionConfiguration = new EgressActionConfiguration(egressActionName, null);
            egressFlow.setEgressAction(egressActionConfiguration);

            egressFlow.setFlowStatus(FlowStatus.newBuilder().state(flowState).testMode(testMode).build());

            return egressFlow;
        }
    }
}
