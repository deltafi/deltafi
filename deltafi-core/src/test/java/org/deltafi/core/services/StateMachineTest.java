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
import lombok.Singular;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.util.Util;
import org.deltafi.core.exceptions.MissingEgressFlowException;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.EnrichFlow;
import org.deltafi.core.types.IngressFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.common.constant.DeltaFiConstants.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class StateMachineTest {

    private static final String SOURCE_KEY = "sourceKey";
    private static final String METADATA_KEY = "metadataKey";
    private static final String DOMAIN = "domain";
    private static final String ENRICH = "enrich";
    private static final String ENRICH_ACTION = "TheEnrichAction";
    private static final String VALIDATE_ACTION = "TheValidateAction";
    private static final String FORMAT_ACTION = "TheFormatAction";
    private static final String EGRESS_ACTION = "TheEgressAction";
    
    private static final String INGRESS_FLOW = "TheIngressFlow";
    private static final String EGRESS_FLOW = "TheEgressFlow";
    private static final String ENRICH_FLOW = "TheEnrichFlow";

    @InjectMocks
    StateMachine stateMachine;

    @Mock
    EnrichFlowService enrichFlowService;

    @Mock
    EgressFlowService egressFlowService;

    @Mock
    IngressFlowService ingressFlowService;

    @Mock
    @SuppressWarnings("unused")
    IdentityService identityService;

    @Spy
    @SuppressWarnings("unused")
    DeltaFiPropertiesService deltaFiPropertiesService = new MockDeltaFiPropertiesService();

    @Test
    void testGetEnrichActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        EnrichFlow enrichFlow = EnrichFlowMaker.builder().build().makeEnrichFlow();

        List<ActionInput> actionInputs = stateMachine.nextEnrichActions(enrichFlow, deltaFile, false);

        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetEnrichActionsMatchesDomainAndEnrichment() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .build().makeEnrichFlow();

        List<ActionInput> actionInputs = stateMachine.nextEnrichActions(enrichFlow, deltaFile, false);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetEnrichActionsDomainDoesNotMatch() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain("otherDomain")
                .enrichRequiresEnrichment(ENRICH)
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile, false)).isEmpty();
    }

    @Test
    void testGetEnrichActionsEnrichmentDoesNotMatch() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment("otherEnrich")
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile, false)).isEmpty();
    }

    @Test
    void testGetEnrichActionsDoesNotMatchMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue("wrongKey", "value"))
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile, false)).isEmpty();
    }

    @Test
    void testGetEnrichActionsNoMetadataAvailable() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(SOURCE_KEY, "value"))
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile, false)).isEmpty();
    }

    @Test
    void testGetEnrichActionsMatchesSourceInfoMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(SOURCE_KEY, "value"))
                .build().makeEnrichFlow();

        List<ActionInput> actionInputs = stateMachine.nextEnrichActions(enrichFlow, deltaFile, false);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetEnrichActionsMatchesMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(METADATA_KEY, "value"))
                .build().makeEnrichFlow();

        List<ActionInput> actionInputs = stateMachine.nextEnrichActions(enrichFlow, deltaFile, false);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetFormatActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        addCompletedActions(deltaFile, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        List<Pair<ActionInput, ActionType>> actionInputs = stateMachine.nextEgressFlowActions(egressFlow, deltaFile, false);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getFirst().getActionContext().getName().equals(FORMAT_ACTION));
    }

    @Test
    void testGetFormatActionsAlreadyComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        addCompletedActions(deltaFile, ENRICH_ACTION, FORMAT_ACTION, VALIDATE_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        List<Pair<ActionInput, ActionType>> actionInputs = stateMachine.nextEgressFlowActions(egressFlow, deltaFile, false);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getFirst().getActionContext().getName().equals(EGRESS_ACTION));
    }

    @Test
    void testGetFormatActionsMatchesDomainAndEnrichment() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();
        addCompletedActions(deltaFile, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain(DOMAIN)
                .formatRequiresEnrichment(ENRICH)
                .build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile, false))
                .hasSize(1).matches((list) -> list.get(0).getFirst().getActionContext().getName().equals(FORMAT_ACTION));
    }

    @Test
    void testGetFormatActionsDomainDiffers() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();
        addCompletedActions(deltaFile, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain("otherDomain")
                .formatRequiresEnrichment(ENRICH)
                .build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile, false)).isEmpty();
    }

    @Test
    void testGetFormatActionsEnrichDiffers() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();
        addCompletedActions(deltaFile, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain(DOMAIN)
                .formatRequiresEnrichment("otherEnrich")
                .build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile, false)).isEmpty();
    }

    @Test
    void testGetValidateActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        addCompletedActions(deltaFile, ENRICH_ACTION, FORMAT_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile, false))
                .hasSize(1).matches((list) -> list.get(0).getFirst().getActionContext().getName().equals(VALIDATE_ACTION));

    }

    @Test
    void testGetValidateActionsNoFormatErrorAllowed() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        addCompletedActions(deltaFile, ENRICH_ACTION);

        deltaFile.queueAction(FORMAT_ACTION, ActionType.FORMAT);
        deltaFile.errorAction(FORMAT_ACTION, null, null, "failed", "failed");

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile, false)).isEmpty();
    }

    @Test
    void testGetValidateActionsAlreadyComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        addCompletedActions(deltaFile, ENRICH_ACTION, FORMAT_ACTION, VALIDATE_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile, false))
                .hasSize(1).matches((list) -> list.get(0).getFirst().getActionContext().getName().equals(EGRESS_ACTION));

    }

    @Test
    void testAdvanceToMultipleValidateAction() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        addCompletedActions(deltaFile, ENRICH_ACTION, FORMAT_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().validateActions(List.of("ValidateAction1", "ValidateAction2")).build().makeEgressFlow();
        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(List.of(egressFlow));

        stateMachine.advance(deltaFile);

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(deltaFile.actionNamed("ValidateAction1")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
        assertThat(deltaFile.actionNamed("ValidateAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
    }

    @Test
    void testAdvanceCompleteValidateAction_onePending() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        Action formatAction = Action.newBuilder().state(ActionState.COMPLETE).name(FORMAT_ACTION).build();
        Action completedAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction1").build();
        Action dispatchedAction = Action.newBuilder().state(ActionState.QUEUED).name("ValidateAction2").build();

        deltaFile.setActions(new ArrayList<>(List.of(formatAction, completedAction, dispatchedAction)));
        deltaFile.setEgress(Collections.singletonList(Egress.newBuilder().flow(EGRESS_FLOW).build()));

        EgressFlow egressFlow = EgressFlowMaker.builder().name(EGRESS_FLOW).validateActions(List.of("ValidateAction1", "ValidateAction2")).build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(List.of(egressFlow));

        stateMachine.advance(deltaFile);

        // TODO - is this really testing anything?
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(completedAction.getState()).isEqualTo(ActionState.COMPLETE);
        assertThat(dispatchedAction.getState()).isEqualTo(ActionState.QUEUED);
    }

    @Test
    void testAdvanceCompleteValidateAction_allComplete() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        Action formatAction = Action.newBuilder().state(ActionState.COMPLETE).name(FORMAT_ACTION).build();
        Action completedAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction1").type(ActionType.VALIDATE).build();
        Action dispatchedAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction2").type(ActionType.VALIDATE).build();

        deltaFile.setActions(new ArrayList<>(Arrays.asList(formatAction, completedAction, dispatchedAction)));

        EgressFlow egressFlow = EgressFlowMaker.builder().validateActions(List.of("ValidateAction1", "ValidateAction2")).build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(List.of(egressFlow));

        stateMachine.advance(deltaFile);

        // TODO - is this really testing anything?
        assertThat(completedAction.getState()).isEqualTo(ActionState.COMPLETE);
        assertThat(dispatchedAction.getState()).isEqualTo(ActionState.COMPLETE);
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
    }

    @Test
    void testAdvanceMultipleEgressFlows() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, "EnrichAction2");

        EgressFlow egressFlowAtEnrich = EgressFlowMaker.builder()
                .egressActionName("EgressAction1")
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .build().makeEgressFlow();

        EgressFlow egressFlowAtFormat = EgressFlowMaker.builder()
                .egressActionName("EgressAction2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(List.of(egressFlowAtEnrich, egressFlowAtFormat));

        List<ActionInput> actionInputs = stateMachine.advance(deltaFile);
        assertThat(actionInputs).hasSize(2);

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(actionInputs.get(0)).matches(actionInput -> "FormatAction1".equals(actionInput.getActionContext().getName()));
        assertThat(actionInputs.get(1)).matches(actionInput -> "FormatAction2".equals(actionInput.getActionContext().getName()));
        assertThat(deltaFile.getEgress().stream().map(Egress::getFlow).toList()).containsExactly(EGRESS_FLOW);
    }

    @Test
    void testAdvanceToEgressAction() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, "EnrichAction1", "EnrichAction2", "FormatAction1", "FormatAction2", "ValidateAction1", "ValidateAction2");

        EgressFlow egress1 = EgressFlowMaker.builder()
                .egressActionName("EgressAction1")
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .build().makeEgressFlow();

        EgressFlow egress2 = EgressFlowMaker.builder()
                .egressActionName("EgressAction2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(List.of(egress1, egress2));

        List<ActionInput> actionInputs = stateMachine.advance(deltaFile);
        assertThat(actionInputs).hasSize(2);

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(actionInputs.get(0)).matches(actionInput -> "EgressAction1".equals(actionInput.getActionContext().getName()));
        assertThat(actionInputs.get(1)).matches(actionInput -> "EgressAction2".equals(actionInput.getActionContext().getName()));
        assertThat(deltaFile.actionNamed("EgressAction1")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
        assertThat(deltaFile.actionNamed("EgressAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
        assertThat(deltaFile.getEgress().stream().map(Egress::getFlow).toList()).containsExactly(EGRESS_FLOW);
    }

    @Test
    void testAdvanceToEgressActionWhenInEgressTestMode() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, "EnrichAction1", "EnrichAction2", "FormatAction1", "FormatAction2", "ValidateAction1", "ValidateAction2");

        EgressFlow egress1 = EgressFlowMaker.builder()
                .name("TestEgressFlow")
                .egressActionName("EgressAction1")
                .formatActionName("FormatAction1")
                .testMode(true)
                .validateActions(List.of("ValidateAction1"))
                .build().makeEgressFlow();

        EgressFlow egress2 = EgressFlowMaker.builder()
                .egressActionName("EgressAction2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(List.of(egress1, egress2));

        List<ActionInput> actionInputs = stateMachine.advance(deltaFile);
        assertThat(actionInputs).hasSize(1);

        String TEST_EGRESS_ACTION = "TestEgressFlow." + SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS;

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(actionInputs.get(0)).matches(actionInput -> "EgressAction2".equals(actionInput.getActionContext().getName()));
        assertThat(deltaFile.actionNamed(TEST_EGRESS_ACTION)).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.actionNamed("EgressAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
        assertThat(deltaFile.getEgress().stream().map(Egress::getFlow).toList()).containsExactlyInAnyOrder(EGRESS_FLOW, "TestEgressFlow");
        assertTrue(deltaFile.getTestMode());
        assertThat(deltaFile.getTestModeReason()).isEqualTo("Egress flow 'TestEgressFlow' in test mode");
    }

    @Test
    void testAdvanceToEgressActionWhenInIngressTestMode() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, "EnrichAction1", "EnrichAction2", "FormatAction1", "FormatAction2", "ValidateAction1", "ValidateAction2");

        IngressFlow ingress = IngressFlowMaker.builder()
                .name(INGRESS_FLOW)
                .testMode(true)
                .flowState(FlowState.RUNNING).build().makeIngressFlow();

        EgressFlow egress1 = EgressFlowMaker.builder()
                .name("TestEgressFlow")
                .egressActionName("EgressAction1")
                .formatActionName("FormatAction1")
                .testMode(false)
                .validateActions(List.of("ValidateAction1"))
                .build().makeEgressFlow();

        EgressFlow egress2 = EgressFlowMaker.builder()
                .egressActionName("EgressAction2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(List.of(egress1, egress2));
        Mockito.when(ingressFlowService.getRunningFlowByName(INGRESS_FLOW)).thenReturn(ingress);

        List<ActionInput> actionInputs = stateMachine.advance(deltaFile);
        assertThat(actionInputs).hasSize(0);

        String TEST_EGRESS_ACTION = "TestEgressFlow." + SYNTHETIC_EGRESS_ACTION_FOR_TEST_INGRESS;
        String EGRESS_ACTION = EGRESS_FLOW + "." + SYNTHETIC_EGRESS_ACTION_FOR_TEST_INGRESS;

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
        assertThat(deltaFile.actionNamed(TEST_EGRESS_ACTION)).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.actionNamed(EGRESS_ACTION)).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.getEgress().stream().map(Egress::getFlow).toList()).containsExactlyInAnyOrder(EGRESS_FLOW, "TestEgressFlow");
        assertTrue(deltaFile.getTestMode());
        assertThat(deltaFile.getTestModeReason()).isEqualTo("Ingress flow 'TheIngressFlow' in test mode");
    }

    @Test
    void testAdvanceCompleteEgressAction_onePending() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.setEgress(List.of(Egress.newBuilder().flow(EGRESS_FLOW).build(), Egress.newBuilder().flow(EGRESS_FLOW + "2").build()));

        deltaFile.queueNewAction("EgressAction2", ActionType.EGRESS);
        addCompletedActions(deltaFile, "EnrichAction1", "EnrichAction2", "FormatAction1", "FormatAction2", "ValidateAction1", "ValidateAction2", "EgressAction1");

        EgressFlow egress1 = EgressFlowMaker.builder()
                .name(EGRESS_FLOW)
                .egressActionName("EgressAction1")
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .build().makeEgressFlow();

        EgressFlow egress2 = EgressFlowMaker.builder()
                .name(EGRESS_FLOW + "2")
                .egressActionName("EgressAction2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(List.of(egress1, egress2));

        assertThat(stateMachine.advance(deltaFile)).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(deltaFile.actionNamed("EgressAction1")).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.actionNamed("EgressAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
    }

    @Test
    void testAdvanceCompleteEgressAction_allComplete() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.setEgress(List.of(Egress.newBuilder().flow(EGRESS_FLOW).build(), Egress.newBuilder().flow(EGRESS_FLOW + "2").build()));

        addCompletedActions(deltaFile, "EnrichAction1", "EnrichAction2", "FormatAction1", "FormatAction2", "ValidateAction1", "ValidateAction2", "EgressAction1", "EgressAction2");

        EgressFlow egress1 = EgressFlowMaker.builder()
                .name(EGRESS_FLOW)
                .egressActionName("EgressAction1")
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .build().makeEgressFlow();

        EgressFlow egress2 = EgressFlowMaker.builder()
                .name(EGRESS_FLOW + "2")
                .egressActionName("EgressAction2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(List.of(egress1, egress2));

        assertThat(stateMachine.advance(deltaFile)).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
        assertThat(deltaFile.actionNamed("EgressAction1")).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.actionNamed("EgressAction2")).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
    }

    @Test
    void testNoEgressFlowConfiguredIsAnError() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.ENRICH);

        Mockito.when(enrichFlowService.getRunningFlows()).thenReturn(Collections.emptyList());
        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(Collections.emptyList());

        assertThrows(MissingEgressFlowException.class, () -> stateMachine.advance(deltaFile));
    }

    @Test
    void testNoEgressFlowCheckSkippedForErrorActions() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.ENRICH);
        deltaFile.queueNewAction("ErrorEnrichAction", ActionType.ENRICH);
        deltaFile.errorAction(ActionEvent.newBuilder()
                .did(deltaFile.getDid())
                .action("ErrorEnrichAction")
                .error(ErrorEvent.newBuilder()
                        .context("context")
                        .cause("cause")
                        .build())
                .build());

        assertThat(stateMachine.advance(deltaFile)).isEmpty();
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.ERROR);
    }

    @Test
    void testNoEgressFlowRequiredForSplitLoadActions() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.INGRESS);
        deltaFile.queueNewAction("SplitLoadAction", ActionType.LOAD);
        deltaFile.reinjectAction(ActionEvent.newBuilder()
                .did(deltaFile.getDid())
                .action("SplitLoadAction")
                .build());

        assertThat(stateMachine.advance(deltaFile)).isEmpty();
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
    }

    @Test
    void testNoEgressFlowRequiredForFilteredLoadActions() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.INGRESS);
        deltaFile.queueNewAction("FilteredLoadAction", ActionType.LOAD);
        deltaFile.filterAction(ActionEvent.newBuilder()
                .did(deltaFile.getDid())
                .action("FilteredLoadAction")
                .build(), "filtered");

        assertThat(stateMachine.advance(deltaFile)).isEmpty();
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
    }

    private void addCompletedActions(DeltaFile deltaFile, String... actions) {
        for (String action : actions) {
            deltaFile.queueAction(action, ActionType.UNKNOWN);
            deltaFile.completeAction(action, null, null);
        }
    }

    @Test
    void testAdvanceEnrichFlows_onePending() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.ENRICH);
        deltaFile.addDomain(DOMAIN, "value", MediaType.ALL_VALUE);
        deltaFile.queueNewAction("EnrichAction1", ActionType.ENRICH);
        addCompletedActions(deltaFile, "DomainAction1");

        EnrichFlow enrich1 = EnrichFlowMaker.builder()
                .name(ENRICH_FLOW)
                .domainActionName("DomainAction1")
                .enrichActionName(null)
                .requiresDomain(DOMAIN)
                .build().makeEnrichFlow();

        EnrichFlow enrich2 = EnrichFlowMaker.builder()
                .name(ENRICH_FLOW + "2")
                .domainActionName(null)
                .enrichActionName("EnrichAction1")
                .requiresDomain(DOMAIN)
                .build().makeEnrichFlow();

        Mockito.when(enrichFlowService.getRunningFlows()).thenReturn(List.of(enrich1, enrich2));

        // mock advancing a DeltaFile after a domain completes from one flow and enrich action is pending in another flow
        assertThat(stateMachine.advance(deltaFile)).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.ENRICH);
        assertThat(deltaFile.actionNamed("EnrichAction1")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
    }

    private DeltaFile makeDomainAndEnrichFile() {
        return makeCustomFile(false);
    }

    private DeltaFile makeDeltaFile() {
        return makeCustomFile(true);
    }

    private DeltaFile makeCustomFile(boolean withSourceInfo) {
        Content content = new Content("name", "application/octet-stream", List.of(new Segment("objectName", 0, 500, "did")));
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW, List.of(content), Map.of(METADATA_KEY, "value"));
        deltaFile.addDomain(DOMAIN, "value", MediaType.ALL_VALUE);
        deltaFile.addEnrichment(ENRICH, "value", MediaType.ALL_VALUE);

        if (withSourceInfo) {
            deltaFile.setSourceInfo(new SourceInfo("input.txt", "sample", Map.of(SOURCE_KEY, "value")));
        }

        return deltaFile;
    }

    @Builder
    private static class IngressFlowMaker {
        @Builder.Default
        final String name = INGRESS_FLOW;
        @Builder.Default
        final FlowState flowState = FlowState.STOPPED;
        @Builder.Default
        final boolean testMode = false;

        private IngressFlow makeIngressFlow() {
            IngressFlow ingressFlow = new IngressFlow();
            ingressFlow.setName(name);
            ingressFlow.setFlowStatus(FlowStatus.newBuilder().state(flowState).testMode(testMode).build());

            return ingressFlow;
        }

    }

    @Builder
    private static class EgressFlowMaker {
        @Singular
        List<String> formatRequiresDomains;
        @Singular("formatRequiresEnrichment")
        List<String> formatRequiresEnrichment;
        @Builder.Default
        final String formatActionName = FORMAT_ACTION;
        @Builder.Default
        final List<String> validateActions = List.of(VALIDATE_ACTION);
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

            FormatActionConfiguration formatActionConfiguration = new FormatActionConfiguration(formatActionName, null, formatRequiresDomains);
            formatActionConfiguration.setRequiresEnrichments(formatRequiresEnrichment);
            egressFlow.setFormatAction(formatActionConfiguration);
            egressFlow.setValidateActions(validateActions.stream().map(this::named).toList());

            egressFlow.setFlowStatus(FlowStatus.newBuilder().state(flowState).testMode(testMode).build());

            return egressFlow;
        }

        ValidateActionConfiguration named(String name) {
            return new ValidateActionConfiguration(name, null);
        }
    }

    @Builder
    private static class EnrichFlowMaker {

        @Builder.Default
        final String name = ENRICH_FLOW;
        @Singular
        List<String> requiresDomains;
        @Singular("enrichRequiresEnrichment")
        List<String> enrichRequiresEnrichment;
        @Singular("enrichRequiresMetadata")
        List<KeyValue> enrichRequiresMetadata;
        @Builder.Default
        final String enrichActionName = ENRICH_ACTION;
        final String domainActionName;

        private EnrichFlow makeEnrichFlow() {
            EnrichFlow enrichFlow = new EnrichFlow();
            enrichFlow.setName(name);

            if (domainActionName != null) {
                DomainActionConfiguration domain = new DomainActionConfiguration(domainActionName, null, requiresDomains);
                enrichFlow.setDomainActions(List.of(domain));
            }

            if (enrichActionName != null) {
                EnrichActionConfiguration enrich = new EnrichActionConfiguration(enrichActionName, null, requiresDomains);
                enrich.setRequiresEnrichments(enrichRequiresEnrichment);
                enrich.setRequiresMetadataKeyValues(enrichRequiresMetadata);
                enrichFlow.setEnrichActions(List.of(enrich));
            }

            return enrichFlow;
        }
    }
}
