package org.deltafi.core.domain.services;

import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.configuration.EgressFlowConfiguration;
import org.deltafi.core.domain.generated.types.Action;
import org.deltafi.core.domain.generated.types.ActionState;
import org.deltafi.core.domain.generated.types.DeltaFileStage;
import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class StateMachineTest {

    @InjectMocks
    StateMachine stateMachine;

    @Mock
    @SuppressWarnings("unused")
    ZipkinService zipkinService;

    @Mock
    DeltaFiConfigService deltaFiConfigService;

    @Mock
    @SuppressWarnings("unused")
    RedisService redisService;

    @Test
    void testGetFormatActionsNotIncluded() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "notIncludedFlow");
        Action action = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        deltaFile.getActions().add(action);

        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setIncludeIngressFlows(Collections.singletonList("includedFlow"));
        config.setFormatAction("FormatAction");
        config.setEgressAction("TheEgressAction");

        Mockito.when(deltaFiConfigService.getEgressFlows()).thenReturn(List.of(config));

        assertEquals(Collections.emptyList(), stateMachine.getEgressActions(deltaFile));
    }

    @Test
    void testGetFormatActionsIncluded() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "includedFlow");
        Action action = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        deltaFile.getActions().add(action);

        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setIncludeIngressFlows(Collections.singletonList("includedFlow"));
        config.setFormatAction("FormatAction");
        config.setEgressAction("TheEgressAction");

        Mockito.when(deltaFiConfigService.getEgressFlows()).thenReturn(List.of(config));

        assertEquals(Collections.singletonList("TheEgressAction"), stateMachine.getEgressActions(deltaFile));
    }

    @Test
    void testGetFormatActionsExcluded() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "excludedFlow");
        Action action = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        deltaFile.getActions().add(action);

        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setExcludeIngressFlows(Collections.singletonList("excludedFlow"));
        config.setFormatAction("FormatAction");
        config.setEgressAction("TheEgressAction");

        Mockito.when(deltaFiConfigService.getEgressFlows()).thenReturn(List.of(config));

        assertEquals(Collections.emptyList(), stateMachine.getEgressActions(deltaFile));
    }

    @Test
    void testGetFormatActionsNotExcluded() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "notExcludedFlow");
        Action action = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        deltaFile.getActions().add(action);

        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setExcludeIngressFlows(Collections.singletonList("excludedFlow"));
        config.setFormatAction("FormatAction");
        config.setEgressAction("TheEgressAction");

        Mockito.when(deltaFiConfigService.getEgressFlows()).thenReturn(List.of(config));

        assertEquals(Collections.singletonList("TheEgressAction"), stateMachine.getEgressActions(deltaFile));
    }

    @Test
    void testAdvanceToValidateAction() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS.name());

        Action formatAction = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();

        deltaFile.setActions(new ArrayList<>(Collections.singletonList(formatAction)));

        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setFormatAction("FormatAction");
        config.setValidateActions(Arrays.asList("ValidateAction1", "ValidateAction2"));
        config.setEgressAction("TheEgressAction");

        Mockito.when(deltaFiConfigService.getEgressFlows()).thenReturn(List.of(config));

        stateMachine.advance(deltaFile);

        assertEquals(DeltaFileStage.EGRESS.name(), deltaFile.getStage());
        assertEquals(ActionState.QUEUED, deltaFile.actionNamed("ValidateAction1").orElseThrow().getState());
        assertEquals(ActionState.QUEUED, deltaFile.actionNamed("ValidateAction2").orElseThrow().getState());
    }

    @Test
    void testAdvanceCompleteValidateAction_onePending() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS.name());

        Action formatAction = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        Action completedAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction1").build();
        Action dispatchedAction = Action.newBuilder().state(ActionState.QUEUED).name("ValidateAction2").build();

        deltaFile.setActions(Arrays.asList(formatAction, completedAction, dispatchedAction));

        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setFormatAction("FormatAction");
        config.setValidateActions(Arrays.asList("ValidateAction1", "ValidateAction2"));
        config.setEgressAction("TheEgressAction");

        Mockito.when(deltaFiConfigService.getEgressFlows()).thenReturn(List.of(config));

        stateMachine.advance(deltaFile);

        assertEquals(DeltaFileStage.EGRESS.name(), deltaFile.getStage());
        assertEquals(ActionState.COMPLETE, completedAction.getState());
        assertEquals(ActionState.QUEUED, dispatchedAction.getState());
    }

    @Test
    void testAdvanceCompleteValidateAction_allComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS.name());

        Action formatAction = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        Action completedAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction1").build();
        Action dispatchedAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction2").build();

        deltaFile.setActions(new ArrayList<>(Arrays.asList(formatAction, completedAction, dispatchedAction)));

        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setFormatAction("FormatAction");
        config.setValidateActions(Arrays.asList("ValidateAction1", "ValidateAction2"));
        config.setEgressAction("TheEgressAction");

        Mockito.when(deltaFiConfigService.getEgressFlows()).thenReturn(List.of(config));

        stateMachine.advance(deltaFile);

        assertEquals(ActionState.COMPLETE, completedAction.getState());
        assertEquals(ActionState.COMPLETE, dispatchedAction.getState());
        assertEquals(DeltaFileStage.EGRESS.name(), deltaFile.getStage());
    }

    @Test
    void testAdvanceToEgressAction() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS.name());

        Action formatAction = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        Action validateAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction1").build();

        deltaFile.setActions(new ArrayList<>(Arrays.asList(formatAction, validateAction)));

        EgressFlowConfiguration flow1 = new EgressFlowConfiguration();
        flow1.setFormatAction("FormatAction");
        flow1.setValidateActions(Collections.singletonList("ValidateAction1"));

        EgressFlowConfiguration flow2 = new EgressFlowConfiguration();
        flow2.setFormatAction("FormatAction");
        flow2.setValidateActions(Collections.singletonList("ValidateAction1"));

        flow1.setEgressAction("FlowEgressAction");
        flow2.setEgressAction("Flow2EgressAction");

        Mockito.when(deltaFiConfigService.getEgressFlows()).thenReturn(List.of(flow1, flow2));

        stateMachine.advance(deltaFile);

        assertEquals(DeltaFileStage.EGRESS.name(), deltaFile.getStage());
        assertEquals(ActionState.QUEUED, deltaFile.actionNamed("FlowEgressAction").orElseThrow().getState());
        assertEquals(ActionState.QUEUED, deltaFile.actionNamed("Flow2EgressAction").orElseThrow().getState());
    }

    @Test
    void testAdvanceCompleteEgressAction_onePending() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS.name());

        Action formatAction = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        Action validateAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction1").build();
        Action flowEgressAction = Action.newBuilder().state(ActionState.COMPLETE).name("FlowEgressAction").build();
        Action flow2EgressAction = Action.newBuilder().state(ActionState.QUEUED).name("Flow2EgressAction").build();

        deltaFile.setActions(new ArrayList<>(Arrays.asList(formatAction, validateAction, flowEgressAction, flow2EgressAction)));

        EgressFlowConfiguration flow1 = new EgressFlowConfiguration();
        flow1.setFormatAction("FormatAction");
        flow1.setValidateActions(Collections.singletonList("ValidateAction1"));

        EgressFlowConfiguration flow2 = new EgressFlowConfiguration();
        flow2.setFormatAction("FormatAction");
        flow2.setValidateActions(Collections.singletonList("ValidateAction1"));

        flow1.setEgressAction("FlowEgressAction");
        flow2.setEgressAction("Flow2EgressAction");

        Mockito.when(deltaFiConfigService.getEgressFlows()).thenReturn(List.of(flow1, flow2));

        stateMachine.advance(deltaFile);

        assertEquals(DeltaFileStage.EGRESS.name(), deltaFile.getStage());
        assertEquals(ActionState.COMPLETE, deltaFile.actionNamed("FlowEgressAction").orElseThrow().getState());
        assertEquals(ActionState.QUEUED, deltaFile.actionNamed("Flow2EgressAction").orElseThrow().getState());
    }

    @Test
    void testAdvanceCompleteEgressAction_allComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS.name());

        Action formatAction = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        Action validateAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction1").build();
        Action flowEgressAction = Action.newBuilder().state(ActionState.COMPLETE).name("FlowEgressAction").build();
        Action flow2EgressAction = Action.newBuilder().state(ActionState.COMPLETE).name("Flow2EgressAction").build();

        deltaFile.setActions(new ArrayList<>(Arrays.asList(formatAction, validateAction, flowEgressAction, flow2EgressAction)));

        EgressFlowConfiguration flow1 = new EgressFlowConfiguration();
        flow1.setFormatAction("FormatAction");
        flow1.setValidateActions(Collections.singletonList("ValidateAction1"));

        EgressFlowConfiguration flow2 = new EgressFlowConfiguration();
        flow2.setFormatAction("FormatAction");
        flow2.setValidateActions(Collections.singletonList("ValidateAction1"));

        flow1.setEgressAction("FlowEgressAction");
        flow2.setEgressAction("Flow2EgressAction");

        Mockito.when(deltaFiConfigService.getEgressFlows()).thenReturn(List.of(flow1, flow2));

        stateMachine.advance(deltaFile);

        assertEquals(DeltaFileStage.COMPLETE.name(), deltaFile.getStage());
        assertEquals(ActionState.COMPLETE, deltaFile.actionNamed("FlowEgressAction").orElseThrow().getState());
        assertEquals(ActionState.COMPLETE, deltaFile.actionNamed("Flow2EgressAction").orElseThrow().getState());
    }
}