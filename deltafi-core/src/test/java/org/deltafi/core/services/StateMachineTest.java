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
import org.deltafi.core.types.NormalizeFlow;
import org.deltafi.core.types.TransformFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS;
import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST_NORMALIZE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

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
    
    private static final String NORMALIZE_FLOW = "TheNormalizeFlow";
    private static final String EGRESS_FLOW = "TheEgressFlow";
    private static final String ENRICH_FLOW = "TheEnrichFlow";

    @InjectMocks
    StateMachine stateMachine;

    @Mock
    EnrichFlowService enrichFlowService;

    @Mock
    EgressFlowService egressFlowService;

    @Mock
    NormalizeFlowService normalizeFlowService;

    @Mock
    TransformFlowService transformFlowService;

    @Mock
    @SuppressWarnings("unused")
    IdentityService identityService;

    @Spy
    @SuppressWarnings("unused")
    DeltaFiPropertiesService deltaFiPropertiesService = new MockDeltaFiPropertiesService();

    @Mock
    QueueManagementService queueManagementService;

    @BeforeEach
    void setup() {
        Mockito.lenient().when(queueManagementService.coldQueue(anyString(), anyLong())).thenReturn(false);
    }

    @Test
    void testGetEnrichActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        EnrichFlow enrichFlow = EnrichFlowMaker.builder().build().makeEnrichFlow();

        List<EnrichActionConfiguration> enrichActions = stateMachine.nextEnrichActions(enrichFlow, deltaFile);

        assertThat(enrichActions).hasSize(1).matches((list) -> list.get(0).getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetEnrichActionsMatchesDomainAndEnrichment() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .build().makeEnrichFlow();

        List<EnrichActionConfiguration> enrichActions = stateMachine.nextEnrichActions(enrichFlow, deltaFile);
        assertThat(enrichActions).hasSize(1).matches((list) -> list.get(0).getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetEnrichActionsDomainDoesNotMatch() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain("otherDomain")
                .enrichRequiresEnrichment(ENRICH)
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsEnrichmentDoesNotMatch() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment("otherEnrich")
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsDoesNotMatchMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue("wrongKey", "value"))
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsNoMetadataAvailable() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(SOURCE_KEY, "value"))
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsMatchesSourceInfoMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(SOURCE_KEY, "value"))
                .build().makeEnrichFlow();

        List<EnrichActionConfiguration> enrichActions = stateMachine.nextEnrichActions(enrichFlow, deltaFile);
        assertThat(enrichActions).hasSize(1).matches((list) -> list.get(0).getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetEnrichActionsMatchesMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .requiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(METADATA_KEY, "value"))
                .build().makeEnrichFlow();

        List<EnrichActionConfiguration> enrichActions = stateMachine.nextEnrichActions(enrichFlow, deltaFile);
        assertThat(enrichActions).hasSize(1).matches((list) -> list.get(0).getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetFormatActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);

        addCompletedActions(deltaFile, ENRICH_FLOW, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile))
                .hasSize(1)
                .matches((list) -> list.get(0).getSecond().getName().equals(FORMAT_ACTION));
    }

    @Test
    void testGetFormatActionsAlreadyComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);

        addCompletedActions(deltaFile, ENRICH_FLOW, ENRICH_ACTION);
        addCompletedActions(deltaFile, EGRESS_FLOW, FORMAT_ACTION, VALIDATE_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile))
                .hasSize(1)
                .matches((list) -> list.get(0).getSecond().getName().equals(EGRESS_ACTION));
    }

    @Test
    void testGetFormatActionsMatchesDomainAndEnrichment() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        addCompletedActions(deltaFile, ENRICH_FLOW, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain(DOMAIN)
                .formatRequiresEnrichment(ENRICH)
                .build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile))
                .hasSize(1)
                .matches((list) -> list.get(0).getSecond().getName().equals(FORMAT_ACTION));
    }

    @Test
    void testGetFormatActionsDomainDiffers() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        addCompletedActions(deltaFile, ENRICH_FLOW, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain("otherDomain")
                .formatRequiresEnrichment(ENRICH)
                .build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetFormatActionsEnrichDiffers() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        addCompletedActions(deltaFile, ENRICH_FLOW, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain(DOMAIN)
                .formatRequiresEnrichment("otherEnrich")
                .build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetValidateActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);

        addCompletedActions(deltaFile, ENRICH_FLOW, ENRICH_ACTION);
        addCompletedActions(deltaFile, EGRESS_FLOW, FORMAT_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile))
                .hasSize(1)
                .matches((list) -> list.get(0).getSecond().getName().equals(VALIDATE_ACTION));

    }

    @Test
    void testGetValidateActionsNoFormatErrorAllowed() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);

        addCompletedActions(deltaFile, ENRICH_FLOW, ENRICH_ACTION);

        deltaFile.queueAction(EGRESS_FLOW, FORMAT_ACTION, ActionType.FORMAT, false);
        deltaFile.errorAction(EGRESS_FLOW, FORMAT_ACTION, null, null, "failed", "failed");

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetValidateActionsAlreadyComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);

        addCompletedActions(deltaFile, ENRICH_FLOW, ENRICH_ACTION);
        addCompletedActions(deltaFile, EGRESS_FLOW, FORMAT_ACTION, VALIDATE_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.nextEgressFlowActions(egressFlow, deltaFile))
                .hasSize(1)
                .matches((list) -> list.get(0).getSecond().getName().equals(EGRESS_ACTION));

    }

    @Test
    void testAdvanceToMultipleValidateAction() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, ENRICH_FLOW, ENRICH_ACTION);
        addCompletedActions(deltaFile, EGRESS_FLOW, FORMAT_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().validateActions(List.of("ValidateAction1", "ValidateAction2")).build().makeEgressFlow();
        Mockito.when(egressFlowService.getMatchingFlows(NORMALIZE_FLOW)).thenReturn(List.of(egressFlow));

        stateMachine.advance(deltaFile);

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(deltaFile.actionNamed(egressFlow.getName(), "ValidateAction1")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
        assertThat(deltaFile.actionNamed(egressFlow.getName(), "ValidateAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
    }

    @Test
    void testAdvanceCompleteValidateAction_onePending() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        Action formatAction = Action.builder().state(ActionState.COMPLETE).flow(EGRESS_FLOW).name(FORMAT_ACTION).build();
        Action completedAction = Action.builder().state(ActionState.COMPLETE).flow(EGRESS_FLOW).name("ValidateAction1").build();
        Action dispatchedAction = Action.builder().state(ActionState.QUEUED).flow(EGRESS_FLOW).name("ValidateAction2").build();
        deltaFile.setActions(new ArrayList<>(List.of(formatAction, completedAction, dispatchedAction)));

        deltaFile.setEgress(Collections.singletonList(Egress.builder().flow(EGRESS_FLOW).build()));

        EgressFlow egressFlow = EgressFlowMaker.builder().name(EGRESS_FLOW).validateActions(List.of("ValidateAction1", "ValidateAction2")).build().makeEgressFlow();
        Mockito.when(egressFlowService.getMatchingFlows(NORMALIZE_FLOW)).thenReturn(List.of(egressFlow));

        stateMachine.advance(deltaFile);

        assertThat(completedAction.getState()).isEqualTo(ActionState.COMPLETE);
        assertThat(dispatchedAction.getState()).isEqualTo(ActionState.QUEUED);
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
    }

    @Test
    void testAdvanceCompleteValidateAction_allComplete() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        Action formatAction = Action.builder().state(ActionState.COMPLETE).flow(EGRESS_FLOW).name(FORMAT_ACTION).build();
        Action completedAction = Action.builder().state(ActionState.COMPLETE).flow(EGRESS_FLOW).name("ValidateAction1").type(ActionType.VALIDATE).build();
        Action dispatchedAction = Action.builder().state(ActionState.COMPLETE).flow(EGRESS_FLOW).name("ValidateAction2").type(ActionType.VALIDATE).build();
        deltaFile.setActions(new ArrayList<>(Arrays.asList(formatAction, completedAction, dispatchedAction)));

        deltaFile.setEgress(Collections.singletonList(Egress.builder().flow(EGRESS_FLOW).build()));

        EgressFlow egressFlow = EgressFlowMaker.builder().name(EGRESS_FLOW).validateActions(List.of("ValidateAction1", "ValidateAction2")).build().makeEgressFlow();
        Mockito.when(egressFlowService.getMatchingFlows(NORMALIZE_FLOW)).thenReturn(List.of(egressFlow));

        stateMachine.advance(deltaFile);

        assertThat(completedAction.getState()).isEqualTo(ActionState.COMPLETE);
        assertThat(dispatchedAction.getState()).isEqualTo(ActionState.COMPLETE);
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
    }

    @Test
    void testAdvanceMultipleEgressFlows() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, ENRICH_FLOW, "EnrichAction2");

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

        Mockito.when(egressFlowService.getMatchingFlows(NORMALIZE_FLOW)).thenReturn(List.of(egressFlowAtEnrich, egressFlowAtFormat));

        List<ActionInvocation> actionInvocations = stateMachine.advance(deltaFile);
        assertThat(actionInvocations).hasSize(2);

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(actionInvocations.get(0)).matches(actionInput -> "FormatAction1".equals(actionInput.getActionConfiguration().getName()));
        assertThat(actionInvocations.get(1)).matches(actionInput -> "FormatAction2".equals(actionInput.getActionConfiguration().getName()));
        assertThat(deltaFile.getEgress().stream().map(Egress::getFlow).toList()).containsExactly(EGRESS_FLOW);
    }

    @Test
    void testAdvanceToEgressAction() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, ENRICH_FLOW, "EnrichAction1", "EnrichAction2");
        addCompletedActions(deltaFile, EGRESS_FLOW, "FormatAction1", "FormatAction2", "ValidateAction1", "ValidateAction2");

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

        Mockito.when(egressFlowService.getMatchingFlows(NORMALIZE_FLOW)).thenReturn(List.of(egress1, egress2));

        List<ActionInvocation> actionInvocations = stateMachine.advance(deltaFile);
        assertThat(actionInvocations).hasSize(2);

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(actionInvocations.get(0)).matches(actionInput -> "EgressAction1".equals(actionInput.getActionConfiguration().getName()));
        assertThat(actionInvocations.get(1)).matches(actionInput -> "EgressAction2".equals(actionInput.getActionConfiguration().getName()));
        assertThat(deltaFile.actionNamed(egress1.getName(), "EgressAction1")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
        assertThat(deltaFile.actionNamed(egress2.getName(), "EgressAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
        assertThat(deltaFile.getEgress().stream().map(Egress::getFlow).toList()).containsExactly(EGRESS_FLOW);
    }

    @Test
    void testAdvanceToEgressActionWhenInEgressTestMode() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, ENRICH_FLOW, "EnrichAction1", "EnrichAction2");

        EgressFlow egress1 = EgressFlowMaker.builder()
                .name("TestEgressFlow")
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .egressActionName("EgressAction1")
                .testMode(true)
                .build().makeEgressFlow();
        addCompletedActions(deltaFile, egress1.getName(), "FormatAction1", "ValidateAction1");

        EgressFlow egress2 = EgressFlowMaker.builder()
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .egressActionName("EgressAction2")
                .build().makeEgressFlow();
        addCompletedActions(deltaFile, egress2.getName(), "FormatAction2", "ValidateAction2");

        Mockito.when(egressFlowService.getMatchingFlows(NORMALIZE_FLOW)).thenReturn(List.of(egress1, egress2));

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        List<ActionInvocation> actionInvocations = stateMachine.advance(deltaFile);
        assertThat(actionInvocations).hasSize(1);
        assertThat(actionInvocations.get(0)).matches(actionInput -> "EgressAction2".equals(actionInput.getActionConfiguration().getName()));
        assertThat(deltaFile.actionNamed(egress1.getName(), SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS)).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.actionNamed(egress2.getName(), "EgressAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
        assertThat(deltaFile.getEgress().stream().map(Egress::getFlow).toList()).containsExactlyInAnyOrder(EGRESS_FLOW, "TestEgressFlow");
        assertTrue(deltaFile.getTestMode());
        assertThat(deltaFile.getTestModeReason()).isEqualTo("Egress flow 'TestEgressFlow' in test mode");
    }

    @Test
    void testAdvanceToEgressActionWhenInNormalizeTestMode() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, ENRICH_FLOW, "EnrichAction1", "EnrichAction2");

        EgressFlow egress1 = EgressFlowMaker.builder()
                .name("TestEgressFlow")
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .egressActionName("EgressAction1")
                .testMode(false)
                .build().makeEgressFlow();
        addCompletedActions(deltaFile, egress1.getName(), "FormatAction1", "ValidateAction1");

        EgressFlow egress2 = EgressFlowMaker.builder()
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .egressActionName("EgressAction2")
                .build().makeEgressFlow();
        addCompletedActions(deltaFile, egress2.getName(), "FormatAction2", "ValidateAction2");

        Mockito.when(egressFlowService.getMatchingFlows(NORMALIZE_FLOW)).thenReturn(List.of(egress1, egress2));

        NormalizeFlow normalizeFlow = NormalizeFlowMaker.builder()
                .name(NORMALIZE_FLOW)
                .testMode(true)
                .flowState(FlowState.RUNNING).build().makeNormalizeFlow();
        Mockito.when(normalizeFlowService.getFlowOrThrow(NORMALIZE_FLOW)).thenReturn(normalizeFlow);

        List<ActionInvocation> actionInvocations = stateMachine.advance(deltaFile);
        assertThat(actionInvocations).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
        assertThat(deltaFile.actionNamed(egress1.getName(), SYNTHETIC_EGRESS_ACTION_FOR_TEST_NORMALIZE)).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.actionNamed(EGRESS_FLOW, SYNTHETIC_EGRESS_ACTION_FOR_TEST_NORMALIZE)).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.getEgress().stream().map(Egress::getFlow).toList()).containsExactlyInAnyOrder(EGRESS_FLOW, "TestEgressFlow");
        assertTrue(deltaFile.getTestMode());
        assertThat(deltaFile.getTestModeReason()).isEqualTo("Normalize flow 'TheNormalizeFlow' in test mode");
    }

    @Test
    void testAdvanceCompleteEgressAction_onePending() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.setEgress(List.of(Egress.builder().flow(EGRESS_FLOW).build(), Egress.builder().flow(EGRESS_FLOW + "2").build()));

        addCompletedActions(deltaFile, ENRICH_FLOW, "EnrichAction1", "EnrichAction2");

        EgressFlow egress1 = EgressFlowMaker.builder()
                .name(EGRESS_FLOW)
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .egressActionName("EgressAction1")
                .build().makeEgressFlow();
        addCompletedActions(deltaFile, egress1.getName(), "FormatAction1", "ValidateAction1", "EgressAction1");

        EgressFlow egress2 = EgressFlowMaker.builder()
                .name(EGRESS_FLOW + "2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .egressActionName("EgressAction2")
                .build().makeEgressFlow();
        addCompletedActions(deltaFile, egress2.getName(), "FormatAction2", "ValidateAction2");
        deltaFile.queueNewAction(egress2.getName(), "EgressAction2", ActionType.EGRESS, false);

        Mockito.when(egressFlowService.getMatchingFlows(NORMALIZE_FLOW)).thenReturn(List.of(egress1, egress2));

        assertThat(stateMachine.advance(deltaFile)).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(deltaFile.actionNamed(egress1.getName(), "EgressAction1")).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.actionNamed(egress2.getName(), "EgressAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
    }

    @Test
    void testAdvanceCompleteEgressAction_allComplete() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.setEgress(List.of(Egress.builder().flow(EGRESS_FLOW).build(), Egress.builder().flow(EGRESS_FLOW + "2").build()));

        addCompletedActions(deltaFile, ENRICH_FLOW, "EnrichAction1", "EnrichAction2");

        EgressFlow egress1 = EgressFlowMaker.builder()
                .name(EGRESS_FLOW)
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .egressActionName("EgressAction1")
                .build().makeEgressFlow();
        addCompletedActions(deltaFile, egress1.getName(), "FormatAction1", "ValidateAction1", "EgressAction1");

        EgressFlow egress2 = EgressFlowMaker.builder()
                .name(EGRESS_FLOW + "2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .egressActionName("EgressAction2")
                .build().makeEgressFlow();
        addCompletedActions(deltaFile, egress2.getName(), "FormatAction2", "ValidateAction2", "EgressAction2");

        Mockito.when(egressFlowService.getMatchingFlows(NORMALIZE_FLOW)).thenReturn(List.of(egress1, egress2));

        assertThat(stateMachine.advance(deltaFile)).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
        assertThat(deltaFile.actionNamed(egress1.getName(), "EgressAction1")).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.actionNamed(egress2.getName(), "EgressAction2")).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
    }

    @Test
    void testNoEgressFlowConfiguredIsAnError() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.ENRICH);

        Mockito.when(enrichFlowService.getRunningFlows()).thenReturn(Collections.emptyList());
        Mockito.when(egressFlowService.getMatchingFlows(NORMALIZE_FLOW)).thenReturn(Collections.emptyList());

        assertThrows(MissingEgressFlowException.class, () -> stateMachine.advance(deltaFile));
    }

    @Test
    void testNoEgressFlowCheckSkippedForErrorActions() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.ENRICH);
        deltaFile.queueNewAction(ENRICH_FLOW, "ErrorEnrichAction", ActionType.ENRICH, false);
        deltaFile.errorAction(ActionEvent.builder()
                .did(deltaFile.getDid())
                .flow(ENRICH_FLOW)
                .action("ErrorEnrichAction")
                .error(ErrorEvent.builder()
                        .context("context")
                        .cause("cause")
                        .build())
                .build());

        assertThat(stateMachine.advance(deltaFile)).isEmpty();
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.ERROR);
    }

    @Test
    void testNoEgressFlowRequiredForSplitLoadActions() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.INGRESS);
        deltaFile.queueNewAction(NORMALIZE_FLOW, "SplitLoadAction", ActionType.LOAD, false);
        deltaFile.reinjectAction(ActionEvent.builder()
                .did(deltaFile.getDid())
                .flow(NORMALIZE_FLOW)
                .action("SplitLoadAction")
                .build());

        assertThat(stateMachine.advance(deltaFile)).isEmpty();
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
    }

    @Test
    void testNoEgressFlowRequiredForFilteredLoadActions() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.INGRESS);
        deltaFile.queueNewAction(NORMALIZE_FLOW, "FilteredLoadAction", ActionType.LOAD, false);
        deltaFile.filterAction(ActionEvent.builder()
                .did(deltaFile.getDid())
                .flow(NORMALIZE_FLOW)
                .action("FilteredLoadAction")
                .build(), "filtered");

        assertThat(stateMachine.advance(deltaFile)).isEmpty();
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
    }

    private void addCompletedActions(DeltaFile deltaFile, String flow, String... actions) {
        for (String action : actions) {
            deltaFile.queueAction(flow, action, ActionType.UNKNOWN, false);
            deltaFile.completeAction(flow, action, null, null);
        }
    }

    @Test
    void testAdvanceEnrichFlows_onePending() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.ENRICH);

        EnrichFlow enrich1 = EnrichFlowMaker.builder()
                .name(ENRICH_FLOW)
                .domainActionName("DomainAction1")
                .enrichActionName(null)
                .requiresDomain(DOMAIN)
                .build().makeEnrichFlow();
        addCompletedActions(deltaFile, enrich1.getName(), "DomainAction1");

        EnrichFlow enrich2 = EnrichFlowMaker.builder()
                .name(ENRICH_FLOW + "2")
                .domainActionName(null)
                .enrichActionName("EnrichAction1")
                .requiresDomain(DOMAIN)
                .build().makeEnrichFlow();
        deltaFile.queueNewAction(enrich2.getName(), "EnrichAction1", ActionType.ENRICH, false);
        deltaFile.getActions().get(2).addDomain(DOMAIN, "value", MediaType.ALL_VALUE);

        Mockito.when(enrichFlowService.getRunningFlows()).thenReturn(List.of(enrich1, enrich2));

        // mock advancing a DeltaFile after a domain completes from one flow and enrich action is pending in another flow
        assertThat(stateMachine.advance(deltaFile)).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.ENRICH);
        assertThat(deltaFile.actionNamed(enrich2.getName(), "EnrichAction1")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
    }

    @Test
    public void advancesInTransformationFlowWithCollectingTransformAction() {
        TransformFlow transformFlow = new TransformFlow();
        transformFlow.setName(NORMALIZE_FLOW);
        TransformActionConfiguration transformAction = new TransformActionConfiguration("CollectingTransformAction",
                "org.deltafi.action.SomeCollectingTransformAction");
        transformAction.setCollect(new CollectConfiguration(Duration.parse("PT1S"), null, 3, null));
        transformFlow.getTransformActions().add(transformAction);
        transformFlow.getFlowStatus().setState(FlowState.RUNNING);
        Mockito.when(transformFlowService.getRunningFlowByName(NORMALIZE_FLOW)).thenReturn(transformFlow);

        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.getSourceInfo().setProcessingType(ProcessingType.TRANSFORMATION);
        List<ActionInvocation> actionInvocations = stateMachine.advance(deltaFile);

        assertTrue(actionInvocations.isEmpty());
        assertEquals(1, deltaFile.readyToCollectActions().size());
        Action readyToCollectAction = deltaFile.readyToCollectActions().get(0);
        assertEquals(NORMALIZE_FLOW, readyToCollectAction.getFlow());
        assertEquals("CollectingTransformAction", readyToCollectAction.getName());
        assertEquals(ActionType.TRANSFORM, readyToCollectAction.getType());
    }

    @Test
    public void advancesInNormalizationFlowWithCollectingTransformAction() {
        NormalizeFlow normalizeFlow = new NormalizeFlow();
        normalizeFlow.setName(NORMALIZE_FLOW);
        TransformActionConfiguration transformAction = new TransformActionConfiguration("CollectingTransformAction",
                "org.deltafi.action.SomeCollectingTransformAction");
        transformAction.setCollect(new CollectConfiguration(Duration.parse("PT1S"), null, 3, null));
        normalizeFlow.getTransformActions().add(transformAction);
        normalizeFlow.getFlowStatus().setState(FlowState.RUNNING);
        Mockito.when(normalizeFlowService.getRunningFlowByName(NORMALIZE_FLOW)).thenReturn(normalizeFlow);

        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        List<ActionInvocation> actionInvocations = stateMachine.advance(deltaFile);

        assertTrue(actionInvocations.isEmpty());
        assertEquals(1, deltaFile.readyToCollectActions().size());
        Action readyToCollectAction = deltaFile.readyToCollectActions().get(0);
        assertEquals(NORMALIZE_FLOW, readyToCollectAction.getFlow());
        assertEquals("CollectingTransformAction", readyToCollectAction.getName());
        assertEquals(ActionType.TRANSFORM, readyToCollectAction.getType());
    }

    @Test
    public void advancesInNormalizationFlowWithCollectingLoadAction() {
        NormalizeFlow normalizeFlow = new NormalizeFlow();
        normalizeFlow.setName(NORMALIZE_FLOW);
        LoadActionConfiguration loadAction = new LoadActionConfiguration("CollectingLoadAction",
                "org.deltafi.action.SomeCollectingLoadAction");
        loadAction.setCollect(new CollectConfiguration(Duration.parse("PT1S"), null, 3, null));
        normalizeFlow.setLoadAction(loadAction);
        normalizeFlow.getFlowStatus().setState(FlowState.RUNNING);
        Mockito.when(normalizeFlowService.getRunningFlowByName(NORMALIZE_FLOW)).thenReturn(normalizeFlow);

        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        List<ActionInvocation> actionInvocations = stateMachine.advance(deltaFile);

        assertTrue(actionInvocations.isEmpty());
        assertEquals(1, deltaFile.readyToCollectActions().size());
        Action readyToCollectAction = deltaFile.readyToCollectActions().get(0);
        assertEquals(NORMALIZE_FLOW, readyToCollectAction.getFlow());
        assertEquals("CollectingLoadAction", readyToCollectAction.getName());
        assertEquals(ActionType.LOAD, readyToCollectAction.getType());
    }

    @Test
    public void advancesInNormalizationFlowsWithCollectingFormatActions() {
        EgressFlow egressFlow1 = new EgressFlow();
        egressFlow1.setName("egress-1");
        FormatActionConfiguration formatAction1 = new FormatActionConfiguration("CollectingFormatAction",
                "org.deltafi.action.SomeCollectingFormatAction", null);
        formatAction1.setCollect(new CollectConfiguration(Duration.parse("PT1S"), null, 3, null));
        egressFlow1.setFormatAction(formatAction1);
        egressFlow1.getFlowStatus().setState(FlowState.RUNNING);

        EgressFlow egressFlow2 = new EgressFlow();
        egressFlow2.setName("egress-2");
        FormatActionConfiguration formatAction2 = new FormatActionConfiguration("AnotherCollectingFormatAction",
                "org.deltafi.action.SomeOtherCollectingFormatAction", null);
        formatAction2.setCollect(new CollectConfiguration(Duration.parse("PT1S"), null, 3, null));
        egressFlow2.setFormatAction(formatAction2);
        egressFlow2.getFlowStatus().setState(FlowState.RUNNING);

        EgressFlow egressFlow3 = new EgressFlow();
        egressFlow3.setName("egress-3");
        FormatActionConfiguration formatAction3 = new FormatActionConfiguration("NonCollectingFormatAction",
                "org.deltafi.action.SomeNonCollectingFormatAction", null);
        egressFlow3.setFormatAction(formatAction3);
        egressFlow3.getFlowStatus().setState(FlowState.RUNNING);

        Mockito.when(egressFlowService.getMatchingFlows(NORMALIZE_FLOW)).thenReturn(List.of(egressFlow3, egressFlow1,
                egressFlow2));

        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        List<ActionInvocation> actionInvocations = stateMachine.advance(deltaFile);

        assertTrue(actionInvocations.isEmpty());
        assertEquals(1, deltaFile.readyToCollectActions().size());
        Action readyToCollectAction = deltaFile.readyToCollectActions().get(0);
        assertEquals("egress-1", readyToCollectAction.getFlow());
        assertEquals("CollectingFormatAction", readyToCollectAction.getName());
        assertEquals(ActionType.FORMAT, readyToCollectAction.getType());
    }

    private DeltaFile makeDomainAndEnrichFile() {
        return makeCustomFile(false);
    }

    private DeltaFile makeDeltaFile() {
        return makeCustomFile(true);
    }

    private DeltaFile makeCustomFile(boolean withSourceInfo) {
        Content content = new Content("name", "application/octet-stream", List.of(new Segment("objectName", 0, 500, "did")));
        DeltaFile deltaFile = Util.emptyDeltaFile("did", NORMALIZE_FLOW, List.of(content), Map.of(METADATA_KEY, "value"));
        deltaFile.getActions().get(0).addDomain(DOMAIN, "value", MediaType.ALL_VALUE);
        deltaFile.getActions().get(0).addEnrichment(ENRICH, "value", MediaType.ALL_VALUE);

        if (withSourceInfo) {
            deltaFile.setSourceInfo(new SourceInfo("input.txt", "sample", Map.of(SOURCE_KEY, "value"), ProcessingType.NORMALIZATION));
        }

        return deltaFile;
    }

    @Builder
    private static class NormalizeFlowMaker {
        @Builder.Default
        final String name = NORMALIZE_FLOW;
        @Builder.Default
        final FlowState flowState = FlowState.STOPPED;
        @Builder.Default
        final boolean testMode = false;

        private NormalizeFlow makeNormalizeFlow() {
            NormalizeFlow normalizeFlow = new NormalizeFlow();
            normalizeFlow.setName(name);
            normalizeFlow.setFlowStatus(FlowStatus.newBuilder().state(flowState).testMode(testMode).build());

            return normalizeFlow;
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
