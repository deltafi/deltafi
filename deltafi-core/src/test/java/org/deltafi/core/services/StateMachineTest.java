/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.exceptions.MissingFlowException;
import org.deltafi.core.types.JoinEntry;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.generated.types.RateLimit;
import org.deltafi.core.generated.types.RateLimitUnit;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.services.pubsub.PublisherService;
import org.deltafi.core.types.*;
import org.deltafi.core.util.MockFlowDefinitionService;
import org.deltafi.core.util.UtilService;
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
import static org.deltafi.common.constant.DeltaFiConstants.MISSING_FLOW_ACTION;
import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unused")
class StateMachineTest {

    private static final String EGRESS_ACTION = "TheEgressAction";
    
    private static final String TRANSFORM_FLOW = "TheTransformFlow";
    private static final String EGRESS_FLOW = "TheDataSink";

    @Spy private final Clock clock = new TestClock();

    @Mock private RestDataSourceService restDataSourceService;
    @Mock private TimedDataSourceService timedDataSourceService;
    @Mock private OnErrorDataSourceService onErrorDataSourceService;
    @Mock private TransformFlowService transformFlowService;
    @Mock private DataSinkService dataSinkService;
    @Mock private DeltaFiPropertiesService deltaFiPropertiesService;
    @Mock private IdentityService identityService;
    @Mock private QueueManagementService queueManagementService;
    @Mock private JoinEntryService joinEntryService;
    @Mock private PublisherService publisherService;
    @Mock private AnalyticEventService analyticEventService;
    @Mock private MetricService metricService;
    @Mock private RateLimitService rateLimitService;

    private final FlowDefinitionService flowDefinitionService = new MockFlowDefinitionService();
    private final UtilService utilService = new UtilService(flowDefinitionService);

    @InjectMocks private StateMachine stateMachine;

    @Test
    @SuppressWarnings("unchecked")
    void advancesToMultipleDataSinks() {
        DeltaFile deltaFile = utilService.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);

        when(transformFlowService.getActiveFlowByName(TRANSFORM_FLOW)).thenReturn(TransformFlowMaker.builder()
                .name(TRANSFORM_FLOW).flowState(FlowState.RUNNING).build().makeTransformFlow());
        DataSink dataSink1 = DataSinkMaker.builder()
                .egressActionName(EGRESS_ACTION + "1").flowState(FlowState.RUNNING).build().makeDataSink();
        when(dataSinkService.getActiveFlowByName(EGRESS_FLOW + "1")).thenReturn(dataSink1);
        DataSink dataSink2 = DataSinkMaker.builder()
                .egressActionName(EGRESS_ACTION + "2").flowState(FlowState.RUNNING).build().makeDataSink();
        when(dataSinkService.getActiveFlowByName(EGRESS_FLOW + "2")).thenReturn(dataSink2);

        DeltaFileFlow deltaFileTransformFlow = deltaFile.firstFlow();
        deltaFileTransformFlow.getFlowDefinition().setType(FlowType.TRANSFORM);
        // Add flows and set action configurations to simulate PublisherService
        DeltaFileFlow deltaFileDataSink1 = deltaFile.addFlow(FlowDefinition.builder().name(EGRESS_FLOW + "1").type(FlowType.DATA_SINK).build(), deltaFileTransformFlow,
                OffsetDateTime.now(clock));
        deltaFileDataSink1.setPendingActions(List.of(dataSink1.getEgressAction().getName()));
        DeltaFileFlow deltaFileDataSink2 = deltaFile.addFlow(FlowDefinition.builder().name(EGRESS_FLOW + "2").type(FlowType.DATA_SINK).build(), deltaFileTransformFlow,
                OffsetDateTime.now(clock));
        deltaFileDataSink2.setPendingActions(List.of(dataSink2.getEgressAction().getName()));
        TreeSet<DeltaFileFlow> deltaFileDataSinks = new TreeSet<>(Comparator.comparing(DeltaFileFlow::getName));
        deltaFileDataSinks.add(deltaFileDataSink1);
        deltaFileDataSinks.add(deltaFileDataSink2);
        when(publisherService.subscribers(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(deltaFileDataSinks, Collections.emptySet());

        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileTransformFlow);
        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(stateMachineInput));

        assertThat(actionInputs).hasSize(2);
        assertThat(actionInputs.getFirst().getActionContext().getFlowName()).isEqualTo(EGRESS_FLOW + "1");
        assertThat(actionInputs.getFirst().getActionContext().getActionName()).isEqualTo(EGRESS_ACTION + "1");
        assertThat(actionInputs.getLast().getActionContext().getFlowName()).isEqualTo(EGRESS_FLOW + "2");
        assertThat(actionInputs.getLast().getActionContext().getActionName()).isEqualTo(EGRESS_ACTION + "2");
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.IN_FLIGHT);
        assertThat(deltaFile.getDataSinks()).hasSize(2);
        assertThat(deltaFile.getDataSinks().getFirst()).isEqualTo(EGRESS_FLOW + "1");
        assertThat(deltaFile.getDataSinks().getLast()).isEqualTo(EGRESS_FLOW + "2");
    }

    @Test
    void advancesToEgressActionWhenInTransformTestMode() {
        DeltaFile deltaFile = utilService.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);

        DeltaFileFlow deltaFileFlow = deltaFile.firstFlow();
        deltaFileFlow.getFlowDefinition().setType(FlowType.TRANSFORM);
        deltaFileFlow.setTestMode(true);

        TransformFlow transformFlow = TransformFlowMaker.builder()
                .name(TRANSFORM_FLOW)
                .testMode(true)
                .flowState(FlowState.RUNNING).build().makeTransformFlow();
        when(transformFlowService.getActiveFlowByName(TRANSFORM_FLOW)).thenReturn(transformFlow);

        DataSink dataSinkConfig = DataSinkMaker.builder().build().makeDataSink();
        when(dataSinkService.getActiveFlowByName("dataSink"))
                .thenReturn(dataSinkConfig);

        DeltaFileFlow dataSink = new DeltaFileFlow();
        dataSink.setFlowDefinition(FlowDefinition.builder().name("dataSink").type(FlowType.DATA_SINK).build());
        dataSink.setTestMode(true);
        dataSink.setTestModeReason("test mode reason");
        dataSink.setPendingActions(new ArrayList<>(dataSinkConfig.allActionConfigurations().stream().map(ActionConfiguration::getName).toList()));
        deltaFile.getFlows().add(dataSink);
        when(publisherService.subscribers(transformFlow, deltaFile, deltaFileFlow))
                .thenReturn(Set.of(dataSink));

        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileFlow);
        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(stateMachineInput));
        assertThat(actionInputs).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
        assertThat(deltaFile.getFiltered()).isTrue();
        assertThat(dataSink.getActions()).hasSize(1);
        Action egressAction = dataSink.firstAction();
        assertThat(egressAction.getName()).isEqualTo(SYNTHETIC_EGRESS_ACTION_FOR_TEST);
        assertThat(egressAction.getState()).isEqualTo(ActionState.FILTERED);
        assertThat(egressAction.getFilteredCause()).isEqualTo("Filtered by test mode");
        assertThat(egressAction.getFilteredContext()).isEqualTo("Filtered by test mode with a reason of - test mode reason");
    }

    @Test
    void advancesInTransformationFlowWithJoiningTransformAction() {
        DeltaFile deltaFile = utilService.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);

        DeltaFileFlow deltaFileFlow = deltaFile.firstFlow();
        deltaFileFlow.getFlowDefinition().setType(FlowType.TRANSFORM);
        ActionConfiguration transformAction = new ActionConfiguration("JoiningTransformAction", ActionType.TRANSFORM,
                "org.deltafi.action.SomeJoiningTransformAction");
        transformAction.setJoin(new JoinConfiguration(Duration.parse("PT1S"), null, 3, null));
        when(transformFlowService.findRunningActionConfigOrError(TRANSFORM_FLOW, transformAction.getName())).thenReturn(transformAction);
        // add the transform action as the next action config to use in the DeltaFileFlow
        deltaFileFlow.setPendingActions(new ArrayList<>(List.of(transformAction.getName())));

        JoinEntry joinEntry = new JoinEntry();
        joinEntry.setCount(2);
        Mockito.when(joinEntryService.upsertAndLock(Mockito.any(), Mockito.any(), Mockito.isNull(), Mockito.eq(3),
                Mockito.eq(0), Mockito.eq(deltaFile.getDid()))).thenReturn(joinEntry);

        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileFlow);
        List<WrappedActionInput> actionInvocations = stateMachine.advance(List.of(stateMachineInput));

        assertTrue(actionInvocations.isEmpty());
        List<Action> joiningActions = deltaFileFlow.getActions().stream()
                .filter(action -> action.getState().equals(ActionState.JOINING)).toList();
        assertEquals(1, joiningActions.size());
        Action joiningAction = joiningActions.getFirst();
        assertEquals("JoiningTransformAction", joiningAction.getName());
        assertEquals(ActionType.TRANSFORM, joiningAction.getType());
    }

    @Test
    void advancesInTransformationFlowWithJoiningTransformActionNullMaxNum() {
        DeltaFile deltaFile = utilService.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);

        DeltaFileFlow deltaFileFlow = deltaFile.firstFlow();
        deltaFileFlow.getFlowDefinition().setType(FlowType.TRANSFORM);
        ActionConfiguration transformAction = new ActionConfiguration("JoiningTransformAction", ActionType.TRANSFORM,
                "org.deltafi.action.SomeJoiningTransformAction");
        transformAction.setJoin(new JoinConfiguration(Duration.parse("PT1S"), null, null, null));
        when(transformFlowService.findRunningActionConfigOrError(TRANSFORM_FLOW, transformAction.getName())).thenReturn(transformAction);
        // add the transform action as the next action config to use in the DeltaFileFlow
        deltaFileFlow.setPendingActions(new ArrayList<>(List.of(transformAction.getName())));

        JoinEntry joinEntry = new JoinEntry();
        joinEntry.setCount(2);
        Mockito.when(joinEntryService.upsertAndLock(Mockito.any(), Mockito.any(), Mockito.isNull(), Mockito.isNull(),
                Mockito.eq(0), Mockito.eq(deltaFile.getDid()))).thenReturn(joinEntry);

        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileFlow);
        List<WrappedActionInput> actionInvocations = stateMachine.advance(List.of(stateMachineInput));

        assertTrue(actionInvocations.isEmpty());
        List<Action> joiningActions = deltaFileFlow.getActions().stream()
                .filter(action -> action.getState().equals(ActionState.JOINING)).toList();
        assertEquals(1, joiningActions.size());
        Action joiningAction = joiningActions.getFirst();
        assertEquals("JoiningTransformAction", joiningAction.getName());
        assertEquals(ActionType.TRANSFORM, joiningAction.getType());
    }

    @Test
    void existingRateLimitedBacklogQueuesNewDataSinkWorkWithoutConsumingTokens() {
        DeltaFile deltaFile = utilService.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);
        DeltaFileFlow dataSinkFlow = deltaFile.addFlow(
                FlowDefinition.builder().name(EGRESS_FLOW).type(FlowType.DATA_SINK).build(),
                deltaFile.firstFlow(),
                OffsetDateTime.now(clock));
        dataSinkFlow.setPendingActions(new ArrayList<>(List.of(EGRESS_ACTION)));

        DataSink dataSink = DataSinkMaker.builder().name(EGRESS_FLOW).flowState(FlowState.RUNNING).build().makeDataSink();
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(2L)
                .durationSeconds(1)
                .build());

        when(dataSinkService.getActiveFlowByName(EGRESS_FLOW)).thenReturn(dataSink);
        when(queueManagementService.hasRateLimitedBacklog(EGRESS_FLOW)).thenReturn(true);

        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(new StateMachineInput(deltaFile, dataSinkFlow)));

        assertThat(actionInputs).isEmpty();
        assertThat(dataSinkFlow.lastAction().getState()).isEqualTo(ActionState.COLD_QUEUED);
        assertThat(dataSinkFlow.lastAction().isRateLimited()).isTrue();
        Mockito.verify(queueManagementService).markRateLimitedBacklog(EGRESS_FLOW);
        Mockito.verifyNoInteractions(rateLimitService);
    }

    @Test
    void noExistingRateLimitedBacklogConsumesTokenAndQueuesWarmAction() {
        DeltaFile deltaFile = utilService.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);
        DeltaFileFlow dataSinkFlow = deltaFile.addFlow(
                FlowDefinition.builder().name(EGRESS_FLOW).type(FlowType.DATA_SINK).build(),
                deltaFile.firstFlow(),
                OffsetDateTime.now(clock));
        dataSinkFlow.setPendingActions(new ArrayList<>(List.of(EGRESS_ACTION)));

        DataSink dataSink = DataSinkMaker.builder().name(EGRESS_FLOW).flowState(FlowState.RUNNING).build().makeDataSink();
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(2L)
                .durationSeconds(1)
                .build());

        when(dataSinkService.getActiveFlowByName(EGRESS_FLOW)).thenReturn(dataSink);
        when(queueManagementService.hasRateLimitedBacklog(EGRESS_FLOW)).thenReturn(false);
        when(rateLimitService.tryConsume("datasink:" + EGRESS_FLOW, 1L, 2L, Duration.ofSeconds(1))).thenReturn(true);
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(new StateMachineInput(deltaFile, dataSinkFlow)));

        assertThat(actionInputs).hasSize(1);
        assertThat(dataSinkFlow.lastAction().getState()).isEqualTo(ActionState.QUEUED);
        Mockito.verify(rateLimitService).tryConsume("datasink:" + EGRESS_FLOW, 1L, 2L, Duration.ofSeconds(1));
    }

    @Test
    void advancesDataSourceFlowWithCompletedIngress() {
        // Simulates a REST data source flow that was paused after ingress completed but before publishing
        DeltaFile deltaFile = utilService.emptyDeltaFile(UUID.randomUUID(), "rest-data-source");
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);

        DeltaFileFlow dataSourceFlow = deltaFile.firstFlow();
        dataSourceFlow.getFlowDefinition().setType(FlowType.REST_DATA_SOURCE);
        dataSourceFlow.setState(DeltaFileFlowState.IN_FLIGHT);

        // Add a completed ingress action
        Action ingressAction = dataSourceFlow.addAction("IngressAction", null, ActionType.INGRESS,
                ActionState.COMPLETE, OffsetDateTime.now(clock));
        ingressAction.setContent(List.of(new Content("test.txt", "text/plain", List.of())));

        // Mock the REST data source
        RestDataSource restDataSource = new RestDataSource();
        restDataSource.setName("rest-data-source");
        restDataSource.setTopic("test-topic");
        when(restDataSourceService.getActiveFlowByName("rest-data-source")).thenReturn(restDataSource);

        // Mock publisher service to add a subscriber flow
        DeltaFileFlow subscriberFlow = deltaFile.addFlow(
                FlowDefinition.builder().name("subscriber-transform").type(FlowType.TRANSFORM).build(),
                dataSourceFlow, OffsetDateTime.now(clock));
        subscriberFlow.setPendingActions(List.of("TransformAction"));

        when(publisherService.subscribers(Mockito.eq(restDataSource), Mockito.eq(deltaFile), Mockito.eq(dataSourceFlow)))
                .thenReturn(Set.of(subscriberFlow));

        // Mock transform flow for the subscriber
        TransformFlow transformFlow = TransformFlowMaker.builder()
                .name("subscriber-transform").flowState(FlowState.RUNNING).build().makeTransformFlow();
        ActionConfiguration transformAction = new ActionConfiguration("TransformAction", ActionType.TRANSFORM,
                "org.deltafi.action.SomeTransformAction");
        transformFlow.setTransformActions(List.of(transformAction));
        when(transformFlowService.findRunningActionConfigOrError("subscriber-transform", "TransformAction"))
                .thenReturn(transformAction);

        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());
        when(identityService.getUniqueId()).thenReturn("test-identity");

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, dataSourceFlow);
        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(stateMachineInput));

        // The data source flow should be marked COMPLETE
        assertThat(dataSourceFlow.getState()).isEqualTo(DeltaFileFlowState.COMPLETE);

        // Should have dispatched an action for the subscriber flow
        assertThat(actionInputs).hasSize(1);
        assertThat(actionInputs.getFirst().getActionContext().getFlowName()).isEqualTo("subscriber-transform");
    }

    @Test
    void resumedTerminatedPausedFlowStaysPausedWhenFlowIsPaused() {
        DeltaFile deltaFile = resumedTerminatedPausedTransformFlow();
        DeltaFileFlow deltaFileFlow = deltaFile.firstFlow();

        TransformFlow transformFlow = TransformFlowMaker.builder()
                .name(TRANSFORM_FLOW)
                .flowState(FlowState.PAUSED)
                .build().makeTransformFlow();
        transformFlow.setTransformActions(List.of(new ActionConfiguration("TransformAction", ActionType.TRANSFORM,
                "org.deltafi.action.SomeTransformAction")));
        when(transformFlowService.getActiveFlowByName(TRANSFORM_FLOW)).thenReturn(transformFlow);

        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(new StateMachineInput(deltaFile, deltaFileFlow)));

        assertThat(actionInputs).isEmpty();
        assertThat(deltaFileFlow.getState()).isEqualTo(DeltaFileFlowState.PAUSED);
        assertThat(deltaFileFlow.lastAction().getName()).isEqualTo(DeltaFileFlow.TERMINATE_PAUSED_FLOW);
        assertThat(deltaFileFlow.lastAction().getState()).isEqualTo(ActionState.RETRIED);
    }

    @Test
    void resumedTerminatedPausedFlowQueuesWhenFlowIsRunning() {
        DeltaFile deltaFile = resumedTerminatedPausedTransformFlow();
        DeltaFileFlow deltaFileFlow = deltaFile.firstFlow();
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        TransformFlow transformFlow = TransformFlowMaker.builder()
                .name(TRANSFORM_FLOW)
                .flowState(FlowState.RUNNING)
                .build().makeTransformFlow();
        ActionConfiguration transformAction = new ActionConfiguration("TransformAction", ActionType.TRANSFORM,
                "org.deltafi.action.SomeTransformAction");
        transformFlow.setTransformActions(List.of(transformAction));
        when(transformFlowService.getActiveFlowByName(TRANSFORM_FLOW)).thenReturn(transformFlow);
        when(identityService.getUniqueId()).thenReturn("test-identity");

        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(new StateMachineInput(deltaFile, deltaFileFlow)));

        assertThat(actionInputs).hasSize(1);
        assertThat(actionInputs.getFirst().getActionContext().getFlowName()).isEqualTo(TRANSFORM_FLOW);
        assertThat(actionInputs.getFirst().getActionContext().getActionName()).isEqualTo("TransformAction");
        assertThat(deltaFileFlow.getState()).isEqualTo(DeltaFileFlowState.IN_FLIGHT);
        assertThat(deltaFileFlow.lastAction().getName()).isEqualTo("TransformAction");
        assertThat(deltaFileFlow.lastAction().getState()).isEqualTo(ActionState.QUEUED);
    }

    @Test
    void resumedTerminatedPausedFlowErrorsWhenFlowIsMissing() {
        DeltaFile deltaFile = resumedTerminatedPausedTransformFlow();
        DeltaFileFlow deltaFileFlow = deltaFile.firstFlow();
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        when(transformFlowService.getActiveFlowByName(TRANSFORM_FLOW))
                .thenThrow(MissingFlowException.notFound(TRANSFORM_FLOW, FlowType.TRANSFORM));

        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(new StateMachineInput(deltaFile, deltaFileFlow)));

        assertThat(actionInputs).isEmpty();
        assertThat(deltaFileFlow.getState()).isEqualTo(DeltaFileFlowState.ERROR);
        assertThat(deltaFileFlow.lastAction().getName()).isEqualTo(MISSING_FLOW_ACTION);
        assertThat(deltaFileFlow.lastAction().getState()).isEqualTo(ActionState.ERROR);
        assertThat(deltaFileFlow.lastAction().getErrorCause()).isEqualTo("The transform is no longer installed");
    }

    @Test
    void marksFlowAsCircularAtMaxDepth() {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);

        DeltaFile deltaFile = utilService.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);

        DeltaFileFlow deltaFileFlow = deltaFile.firstFlow();
        deltaFileFlow.getFlowDefinition().setType(FlowType.TRANSFORM);
        deltaFileFlow.setDepth(deltaFiProperties.getMaxFlowDepth());

        DeltaFileFlow dataSink = new DeltaFileFlow();
        dataSink.setFlowDefinition(FlowDefinition.builder().name("dataSink").type(FlowType.DATA_SINK).build());
        DataSink dataSinkConfig = DataSinkMaker.builder().build().makeDataSink();
        dataSink.setPendingActions(new ArrayList<>(dataSinkConfig.allActionConfigurations().stream().map(ActionConfiguration::getName).toList()));

        StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, deltaFileFlow);
        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(stateMachineInput));

        assertThat(actionInputs).isEmpty();
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.ERROR);
        assertThat(deltaFileFlow.getActions()).hasSize(2);
        Action sytheticAction = deltaFileFlow.lastAction();
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
            transformFlow.setFlowStatus(FlowStatus.newBuilder().state(flowState).testMode(testMode).valid(true).build());
            return transformFlow;
        }
    }

    // --- Data Sink: resumed after terminateAllWithError while paused ---

    @Test
    void resumedTerminatedPausedDataSinkStaysPausedWhenFlowIsPaused() {
        DeltaFile deltaFile = resumedTerminatedPausedDataSinkFlow();
        DeltaFileFlow dataSinkFlow = deltaFile.getFlows().stream()
                .filter(f -> f.getType() == FlowType.DATA_SINK).findFirst().orElseThrow();

        DataSink dataSink = DataSinkMaker.builder()
                .name(EGRESS_FLOW)
                .flowState(FlowState.PAUSED)
                .build().makeDataSink();
        when(dataSinkService.getActiveFlowByName(EGRESS_FLOW)).thenReturn(dataSink);

        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(new StateMachineInput(deltaFile, dataSinkFlow)));

        assertThat(actionInputs).isEmpty();
        assertThat(dataSinkFlow.getState()).isEqualTo(DeltaFileFlowState.PAUSED);
        assertThat(dataSinkFlow.lastAction().getName()).isEqualTo(DeltaFileFlow.TERMINATE_PAUSED_FLOW);
        assertThat(dataSinkFlow.lastAction().getState()).isEqualTo(ActionState.RETRIED);
    }

    @Test
    void resumedTerminatedPausedDataSinkQueuesWhenFlowIsRunning() {
        DeltaFile deltaFile = resumedTerminatedPausedDataSinkFlow();
        DeltaFileFlow dataSinkFlow = deltaFile.getFlows().stream()
                .filter(f -> f.getType() == FlowType.DATA_SINK).findFirst().orElseThrow();
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        DataSink dataSink = DataSinkMaker.builder()
                .name(EGRESS_FLOW)
                .flowState(FlowState.RUNNING)
                .build().makeDataSink();
        when(dataSinkService.getActiveFlowByName(EGRESS_FLOW)).thenReturn(dataSink);
        when(identityService.getUniqueId()).thenReturn("test-identity");

        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(new StateMachineInput(deltaFile, dataSinkFlow)));

        assertThat(actionInputs).hasSize(1);
        assertThat(actionInputs.getFirst().getActionContext().getFlowName()).isEqualTo(EGRESS_FLOW);
        assertThat(actionInputs.getFirst().getActionContext().getActionName()).isEqualTo(EGRESS_ACTION);
        assertThat(dataSinkFlow.getState()).isEqualTo(DeltaFileFlowState.IN_FLIGHT);
        assertThat(dataSinkFlow.lastAction().getName()).isEqualTo(EGRESS_ACTION);
        assertThat(dataSinkFlow.lastAction().getState()).isEqualTo(ActionState.QUEUED);
    }

    @Test
    void resumedTerminatedPausedDataSinkErrorsWhenFlowIsMissing() {
        DeltaFile deltaFile = resumedTerminatedPausedDataSinkFlow();
        DeltaFileFlow dataSinkFlow = deltaFile.getFlows().stream()
                .filter(f -> f.getType() == FlowType.DATA_SINK).findFirst().orElseThrow();

        when(dataSinkService.getActiveFlowByName(EGRESS_FLOW))
                .thenThrow(MissingFlowException.notFound(EGRESS_FLOW, FlowType.DATA_SINK));

        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(new StateMachineInput(deltaFile, dataSinkFlow)));

        assertThat(actionInputs).isEmpty();
        assertThat(dataSinkFlow.getState()).isEqualTo(DeltaFileFlowState.ERROR);
        assertThat(dataSinkFlow.lastAction().getName()).isEqualTo(MISSING_FLOW_ACTION);
        assertThat(dataSinkFlow.lastAction().getState()).isEqualTo(ActionState.ERROR);
        assertThat(dataSinkFlow.lastAction().getErrorCause()).isEqualTo("The data sink is no longer installed");
    }

    private DeltaFile resumedTerminatedPausedDataSinkFlow() {
        DeltaFile deltaFile = utilService.emptyDeltaFile(UUID.randomUUID(), EGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);

        DeltaFileFlow dataSinkFlow = deltaFile.firstFlow();
        dataSinkFlow.getFlowDefinition().setType(FlowType.DATA_SINK);
        dataSinkFlow.setState(DeltaFileFlowState.IN_FLIGHT);
        dataSinkFlow.setPendingActions(new ArrayList<>(List.of(EGRESS_ACTION)));
        dataSinkFlow.addAction(DeltaFileFlow.TERMINATE_PAUSED_FLOW, null, ActionType.UNKNOWN, ActionState.RETRIED, OffsetDateTime.now(clock));

        return deltaFile;
    }

    // --- Data Source: resumed after terminateAllWithError while paused ---

    @Test
    void resumedTerminatedPausedDataSourceStaysPausedWhenFlowIsPaused() {
        DeltaFile deltaFile = resumedTerminatedPausedDataSourceFlow();
        DeltaFileFlow dataSourceFlow = deltaFile.firstFlow();

        RestDataSource restDataSource = new RestDataSource();
        restDataSource.setName("rest-data-source");
        restDataSource.setTopic("test-topic");
        restDataSource.setFlowStatus(FlowStatus.newBuilder().state(FlowState.PAUSED).valid(true).build());
        when(restDataSourceService.getActiveFlowByName("rest-data-source")).thenReturn(restDataSource);

        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(new StateMachineInput(deltaFile, dataSourceFlow)));

        assertThat(actionInputs).isEmpty();
        assertThat(dataSourceFlow.getState()).isEqualTo(DeltaFileFlowState.PAUSED);
        assertThat(dataSourceFlow.lastAction().getName()).isEqualTo(DeltaFileFlow.TERMINATE_PAUSED_FLOW);
        assertThat(dataSourceFlow.lastAction().getState()).isEqualTo(ActionState.RETRIED);
    }

    @Test
    void resumedTerminatedPausedDataSourceAdvancesWhenFlowIsRunning() {
        DeltaFile deltaFile = resumedTerminatedPausedDataSourceFlow();
        DeltaFileFlow dataSourceFlow = deltaFile.firstFlow();

        RestDataSource restDataSource = new RestDataSource();
        restDataSource.setName("rest-data-source");
        restDataSource.setTopic("test-topic");
        restDataSource.setFlowStatus(FlowStatus.newBuilder().state(FlowState.RUNNING).valid(true).build());
        when(restDataSourceService.getActiveFlowByName("rest-data-source")).thenReturn(restDataSource);
        when(publisherService.subscribers(Mockito.eq(restDataSource), Mockito.any(), Mockito.eq(dataSourceFlow)))
                .thenReturn(Collections.emptySet());
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(new DeltaFiProperties());

        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(new StateMachineInput(deltaFile, dataSourceFlow)));

        assertThat(dataSourceFlow.getState()).isEqualTo(DeltaFileFlowState.COMPLETE);
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
    }

    @Test
    void resumedTerminatedPausedDataSourceErrorsWhenFlowIsMissing() {
        DeltaFile deltaFile = resumedTerminatedPausedDataSourceFlow();
        DeltaFileFlow dataSourceFlow = deltaFile.firstFlow();

        when(restDataSourceService.getActiveFlowByName("rest-data-source"))
                .thenThrow(MissingFlowException.notFound("rest-data-source", FlowType.REST_DATA_SOURCE));

        List<WrappedActionInput> actionInputs = stateMachine.advance(List.of(new StateMachineInput(deltaFile, dataSourceFlow)));

        assertThat(actionInputs).isEmpty();
        assertThat(dataSourceFlow.getState()).isEqualTo(DeltaFileFlowState.ERROR);
    }

    private DeltaFile resumedTerminatedPausedDataSourceFlow() {
        DeltaFile deltaFile = utilService.emptyDeltaFile(UUID.randomUUID(), "rest-data-source");
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);

        DeltaFileFlow dataSourceFlow = deltaFile.firstFlow();
        dataSourceFlow.getFlowDefinition().setType(FlowType.REST_DATA_SOURCE);
        dataSourceFlow.setState(DeltaFileFlowState.IN_FLIGHT);
        // Data source has completed ingress action + retried synthetic error
        dataSourceFlow.addAction("IngressAction", null, ActionType.INGRESS, ActionState.COMPLETE, OffsetDateTime.now(clock));
        dataSourceFlow.addAction(DeltaFileFlow.TERMINATE_PAUSED_FLOW, null, ActionType.UNKNOWN, ActionState.RETRIED, OffsetDateTime.now(clock));

        return deltaFile;
    }

    private DeltaFile resumedTerminatedPausedTransformFlow() {
        DeltaFile deltaFile = utilService.emptyDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);

        DeltaFileFlow deltaFileFlow = deltaFile.firstFlow();
        deltaFileFlow.getFlowDefinition().setType(FlowType.TRANSFORM);
        deltaFileFlow.setState(DeltaFileFlowState.IN_FLIGHT);
        deltaFileFlow.setPendingActions(new ArrayList<>(List.of("TransformAction")));
        deltaFileFlow.addAction(DeltaFileFlow.TERMINATE_PAUSED_FLOW, null, ActionType.UNKNOWN, ActionState.RETRIED, OffsetDateTime.now(clock));

        return deltaFile;
    }

    @Builder
    private static class DataSinkMaker {
        @Builder.Default
        final String egressActionName = EGRESS_ACTION;
        @Builder.Default
        final String name = EGRESS_FLOW;
        @Builder.Default
        final FlowState flowState = FlowState.STOPPED;
        @Builder.Default
        final boolean testMode = false;

        private DataSink makeDataSink() {
            DataSink dataSink = new DataSink();
            dataSink.setName(name);
            ActionConfiguration ActionConfiguration = new ActionConfiguration(egressActionName, ActionType.EGRESS, null);
            dataSink.setEgressAction(ActionConfiguration);
            dataSink.setFlowStatus(FlowStatus.newBuilder().state(flowState).testMode(testMode).valid(true).build());
            return dataSink;
        }
    }
}
