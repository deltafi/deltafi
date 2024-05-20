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
import org.deltafi.core.collect.ScheduledCollectService;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.services.pubsub.PublisherService;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.StateMachineInput;
import org.deltafi.core.types.TransformFlow;
import org.deltafi.core.util.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private final TransformFlowService transformFlowService;
    private final QueueManagementService queueManagementService;
    private final CollectEntryService collectEntryService;

    private final StateMachine stateMachine;

    StateMachineTest(@Mock DataSourceService dataSourceService, @Mock TransformFlowService transformFlowService,
                     @Mock EgressFlowService egressFlowService, @Mock IdentityService identityService,
                     @Mock QueueManagementService queueManagementService, @Mock CollectEntryService collectEntryService,
                     @Mock ScheduledCollectService scheduledCollectService, @Mock PublisherService publisherService, @Mock AnalyticEventService analyticEventService) {
        this.transformFlowService = transformFlowService;
        this.queueManagementService = queueManagementService;
        this.collectEntryService = collectEntryService;

        this.stateMachine = new StateMachine(new TestClock(), dataSourceService, transformFlowService, egressFlowService,
                new MockDeltaFiPropertiesService(), identityService, queueManagementService, collectEntryService,
                scheduledCollectService, publisherService, analyticEventService);
    }

    @BeforeEach
    void setup() {
        Mockito.lenient().when(queueManagementService.coldQueue(anyString(), anyLong())).thenReturn(false);
    }

    @Test
    @Disabled("TODO: this is completely busted, needs publish rules set so the next flow gets added")
    void testAdvanceToEgressActionWhenInTransformTestMode() {
        // there should be two tests -- one that shows that test mode gets set, and one that acts on it creating synthetic egress
        DeltaFile deltaFile = Util.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);

        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();
        deltaFileFlow.setTestMode(true);
        deltaFileFlow.setTestModeReason("Transform flow 'TheTransformFlow' in test mode");

        TransformFlow transformFlow = TransformFlowMaker.builder()
                .name(TRANSFORM_FLOW)
                .testMode(true)
                .flowState(FlowState.RUNNING).build().makeTransformFlow();
        Mockito.when(transformFlowService.getRunningFlowByName(TRANSFORM_FLOW)).thenReturn(transformFlow);

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileFlow);
        List<ActionInput> actionInputs = stateMachine.advance(List.of(stateMachineInput));
        assertThat(actionInputs).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
        assertThat(deltaFileFlow.actionNamed(SYNTHETIC_EGRESS_ACTION_FOR_TEST)).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));

        //  TODO what is the equivalent?
//        assertThat(deltaFile.getEgress().stream().map(Egress::getFlow).toList()).containsExactlyInAnyOrder(TRANFORM_FLOW);

        assertTrue(deltaFileFlow.isTestMode());
        assertThat(deltaFileFlow.getTestModeReason()).isEqualTo("Transform flow 'TheTransformFlow' in test mode");
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
