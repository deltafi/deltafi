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
import org.deltafi.core.collect.CollectEntry;
import org.deltafi.core.collect.CollectEntryService;
import org.deltafi.core.collect.ScheduledCollectService;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.services.pubsub.PublisherService;
import org.deltafi.core.types.*;
import org.deltafi.core.util.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unused")
class StateMachineTest {

    private static final String EGRESS_ACTION = "TheEgressAction";
    
    private static final String TRANSFORM_FLOW = "TheTransformFlow";
    private static final String EGRESS_FLOW = "TheEgressFlow";

    @Spy private final Clock clock = new TestClock();

    @Mock private DataSourceService dataSourceService;
    @Mock private TransformFlowService transformFlowService;
    @Mock private EgressFlowService egressFlowService;
    @Mock private DeltaFiPropertiesService deltaFiPropertiesService;
    @Mock private IdentityService identityService;
    @Mock private QueueManagementService queueManagementService;
    @Mock private CollectEntryService collectEntryService;
    @Mock private ScheduledCollectService scheduledCollectService;
    @Mock private PublisherService publisherService;
    @Mock private AnalyticEventService analyticEventService;

    @InjectMocks private StateMachine stateMachine;

    @Test
    @SuppressWarnings("unchecked")
    void advancesToMultipleEgressFlows() {
        DeltaFile deltaFile = Util.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);

        Mockito.when(transformFlowService.getRunningFlowByName(TRANSFORM_FLOW)).thenReturn(TransformFlowMaker.builder()
                .name(TRANSFORM_FLOW).flowState(FlowState.RUNNING).build().makeTransformFlow());
        EgressFlow egressFlow1 = EgressFlowMaker.builder()
                .egressActionName(EGRESS_ACTION + "1").flowState(FlowState.RUNNING).build().makeEgressFlow();
        Mockito.when(egressFlowService.getRunningFlowByName(EGRESS_FLOW + "1")).thenReturn(egressFlow1);
        EgressFlow egressFlow2 = EgressFlowMaker.builder()
                .egressActionName(EGRESS_ACTION + "2").flowState(FlowState.RUNNING).build().makeEgressFlow();
        Mockito.when(egressFlowService.getRunningFlowByName(EGRESS_FLOW + "2")).thenReturn(egressFlow2);

        DeltaFileFlow deltaFileTransformFlow = deltaFile.getFlows().getFirst();
        deltaFileTransformFlow.setType(FlowType.TRANSFORM);
        // Add flows and set action configurations to simulate PublisherService
        DeltaFileFlow deltaFileEgressFlow1 = deltaFile.addFlow(EGRESS_FLOW + "1", FlowType.EGRESS, deltaFileTransformFlow,
                OffsetDateTime.now(clock));
        deltaFileEgressFlow1.setPendingActions(List.of(egressFlow1.getEgressAction().getName()));
        DeltaFileFlow deltaFileEgressFlow2 = deltaFile.addFlow(EGRESS_FLOW + "2", FlowType.EGRESS, deltaFileTransformFlow,
                OffsetDateTime.now(clock));
        deltaFileEgressFlow2.setPendingActions(List.of(egressFlow2.getEgressAction().getName()));
        TreeSet<DeltaFileFlow> deltaFileEgressFlows = new TreeSet<>(Comparator.comparing(DeltaFileFlow::getName));
        deltaFileEgressFlows.add(deltaFileEgressFlow1);
        deltaFileEgressFlows.add(deltaFileEgressFlow2);
        Mockito.when(publisherService.subscribers(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(deltaFileEgressFlows, Collections.emptySet());

        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileTransformFlow);
        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(stateMachineInput));

        assertThat(actionInputs).hasSize(2);
        assertThat(actionInputs.getFirst().getActionContext().getFlowName()).isEqualTo(EGRESS_FLOW + "1");
        assertThat(actionInputs.getFirst().getActionContext().getActionName()).isEqualTo(EGRESS_ACTION + "1");
        assertThat(actionInputs.getLast().getActionContext().getFlowName()).isEqualTo(EGRESS_FLOW + "2");
        assertThat(actionInputs.getLast().getActionContext().getActionName()).isEqualTo(EGRESS_ACTION + "2");
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.IN_FLIGHT);
        assertThat(deltaFile.egressFlowNames()).hasSize(2);
        assertThat(deltaFile.egressFlowNames().getFirst()).isEqualTo(EGRESS_FLOW + "1");
        assertThat(deltaFile.egressFlowNames().getLast()).isEqualTo(EGRESS_FLOW + "2");
    }

    @Test
    void advancesToEgressActionWhenInTransformTestMode() {
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
        egressFlow.setPendingActions(new ArrayList<>(egressFlowConfig.allActionConfigurations().stream().map(ActionConfiguration::getName).toList()));
        Mockito.when(publisherService.subscribers(transformFlow, deltaFile, deltaFileFlow))
                .thenReturn(Set.of(egressFlow));

        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileFlow);
        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(stateMachineInput));
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
        ActionConfiguration transformAction = new ActionConfiguration("CollectingTransformAction",
                ActionType.TRANSFORM, "org.deltafi.action.SomeCollectingTransformAction");
        transformAction.setCollect(new CollectConfiguration(Duration.parse("PT1S"), null, 3, null));
        // add the transform action as the next action config to use in the DeltaFileFlow
        deltaFileFlow.setPendingActions(List.of(transformAction.getName()));

        CollectEntry collectEntry = new CollectEntry();
        collectEntry.setCount(2);
        Mockito.when(collectEntryService.upsertAndLock(Mockito.any(), Mockito.any(), Mockito.isNull(), Mockito.eq(3),
                Mockito.eq(0), Mockito.eq(deltaFile.getDid()))).thenReturn(collectEntry);

        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileFlow);
        List<WrappedActionInput> actionInvocations = stateMachine.advance(List.of(stateMachineInput));

        assertTrue(actionInvocations.isEmpty());
        List<Action> collectingActions = deltaFileFlow.getActions().stream()
                .filter(action -> action.getState().equals(ActionState.COLLECTING)).toList();
        assertEquals(1, collectingActions.size());
        Action collectingAction = collectingActions.getFirst();
        assertEquals("CollectingTransformAction", collectingAction.getName());
        assertEquals(ActionType.TRANSFORM, collectingAction.getType());
    }

    @Test
    public void marksFlowAsCircularAtMaxDepth() {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);

        DeltaFile deltaFile = Util.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);

        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();
        deltaFileFlow.setType(FlowType.TRANSFORM);
        deltaFileFlow.setDepth(deltaFiProperties.getMaxFlowDepth());

        DeltaFileFlow egressFlow = new DeltaFileFlow();
        egressFlow.setName("egressFlow");
        egressFlow.setType(FlowType.EGRESS);
        EgressFlow egressFlowConfig = EgressFlowMaker.builder().build().makeEgressFlow();
        egressFlow.setPendingActions(new ArrayList<>(egressFlowConfig.allActionConfigurations().stream().map(ActionConfiguration::getName).toList()));

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileFlow);
        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(stateMachineInput));

        assertThat(actionInputs).isEmpty();
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.ERROR);
        assertThat(deltaFileFlow.getActions()).hasSize(2);
        Action sytheticAction = deltaFileFlow.getActions().getLast();
        assertThat(sytheticAction.getName()).isEqualTo("CIRCULAR_FLOWS");
        assertThat(sytheticAction.getState()).isEqualTo(ActionState.ERROR);
        assertThat(sytheticAction.getErrorCause()).isEqualTo("Circular flows detected");
        assertThat(sytheticAction.getErrorContext()).isEqualTo("Circular flows detected. Processing stopped at " +
                "maximum depth of " + deltaFiProperties.getMaxFlowDepth());
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
            ActionConfiguration ActionConfiguration = new ActionConfiguration(egressActionName, ActionType.EGRESS, null);
            egressFlow.setEgressAction(ActionConfiguration);
            egressFlow.setFlowStatus(FlowStatus.newBuilder().state(flowState).testMode(testMode).build());
            return egressFlow;
        }
    }
}
