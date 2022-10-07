/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.types.*;
import org.deltafi.core.Util;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.exceptions.MissingEgressFlowException;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.EnrichFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class StateMachineTest {

    private static final String SOURCE_KEY = "sourceKey";
    private static final String PROTOCOL_LAYER_KEY = "protocolLayerKey";
    private static final String DOMAIN = "domain";
    private static final String ENRICH = "enrich";
    private static final String ENRICH_ACTION = "TheEnrichAction";
    private static final String VALIDATE_ACTION = "TheValidateAction";
    private static final String FORMAT_ACTION = "TheFormatAction";
    private static final String EGRESS_ACTION = "TheEgressAction";
    
    private static final String INGRESS_FLOW = "TheIngressFlow";
    private static final String EGRESS_FLOW = "TheEgressFlow";

    @InjectMocks
    StateMachine stateMachine;

    @Mock
    EnrichFlowService enrichFlowService;

    @Mock
    EgressFlowService egressFlowService;

    @Mock
    DeltaFiProperties deltaFiProperties;

    @Test
    void testGetEnrichActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        EnrichFlow enrichFlow = EnrichFlowMaker.builder().build().makeEnrichFlow();

        List<ActionInput> actionInputs = stateMachine.nextEnrichActions(enrichFlow, deltaFile);

        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetEnrichActionsMatchesDomainAndEnrichment() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .build().makeEnrichFlow();

        List<ActionInput> actionInputs = stateMachine.nextEnrichActions(enrichFlow, deltaFile);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetEnrichActionsDomainDoesNotMatch() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .enrichRequiresDomain("otherDomain")
                .enrichRequiresEnrichment(ENRICH)
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsEnrichmentDoesNotMatch() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment("otherEnrich")
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsDoesNotMatchMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue("wrongKey", "value"))
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsNoMetadataAvailable() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(SOURCE_KEY, "value"))
                .build().makeEnrichFlow();

        assertThat(stateMachine.nextEnrichActions(enrichFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsMatchesSourceInfoMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(SOURCE_KEY, "value"))
                .build().makeEnrichFlow();

        List<ActionInput> actionInputs = stateMachine.nextEnrichActions(enrichFlow, deltaFile);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetEnrichActionsMatchesProtocolLayerMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EnrichFlow enrichFlow = EnrichFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(PROTOCOL_LAYER_KEY, "value"))
                .build().makeEnrichFlow();

        List<ActionInput> actionInputs = stateMachine.nextEnrichActions(enrichFlow, deltaFile);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(ENRICH_ACTION));
    }

    @Test
    void testGetFormatActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        addCompletedActions(deltaFile, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        List<ActionInput> actionInputs = stateMachine.advanceEgress(egressFlow, deltaFile);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(FORMAT_ACTION));
    }

    @Test
    void testGetFormatActionsAlreadyComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        addCompletedActions(deltaFile, ENRICH_ACTION, FORMAT_ACTION, VALIDATE_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        List<ActionInput> actionInputs = stateMachine.advanceEgress(egressFlow, deltaFile);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(EGRESS_ACTION));
    }

    @Test
    void testGetFormatActionsMatchesDomainAndEnrichment() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();
        addCompletedActions(deltaFile, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain(DOMAIN)
                .formatRequiresEnrichment(ENRICH)
                .build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile))
                .hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(FORMAT_ACTION));
    }

    @Test
    void testGetFormatActionsDomainDiffers() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();
        addCompletedActions(deltaFile, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain("otherDomain")
                .formatRequiresEnrichment(ENRICH)
                .build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetFormatActionsEnrichDiffers() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();
        addCompletedActions(deltaFile, ENRICH_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain(DOMAIN)
                .formatRequiresEnrichment("otherEnrich")
                .build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetValidateActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        addCompletedActions(deltaFile, ENRICH_ACTION, FORMAT_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile))
                .hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(VALIDATE_ACTION));

    }

    @Test
    void testGetValidateActionsNoFormatErrorAllowed() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        addCompletedActions(deltaFile, ENRICH_ACTION);

        deltaFile.queueAction(FORMAT_ACTION);
        deltaFile.errorAction(FORMAT_ACTION, null, null, "failed", "failed");

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetValidateActionsAlreadyComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        addCompletedActions(deltaFile, ENRICH_ACTION, FORMAT_ACTION, VALIDATE_ACTION);

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile))
                .hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals(EGRESS_ACTION));

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

        EgressFlow egressFlow = EgressFlowMaker.builder().validateActions(List.of("ValidateAction1", "ValidateAction2")).build().makeEgressFlow();

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
        Action completedAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction1").build();
        Action dispatchedAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction2").build();

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
        assertThat(deltaFile.getEgress().stream().map(Egress::getFlow).collect(Collectors.toList())).containsExactly(EGRESS_FLOW);
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
        assertThat(deltaFile.getEgress().stream().map(Egress::getFlow).collect(Collectors.toList())).containsExactly(EGRESS_FLOW);
    }

    @Test
    void testAdvanceCompleteEgressAction_onePending() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        deltaFile.queueNewAction("EgressAction2");
        addCompletedActions(deltaFile, "EnrichAction1", "EnrichAction2", "FormatAction1", "FormatAction2", "ValidateAction1", "ValidateAction2", "EgressAction1");

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

        assertThat(stateMachine.advance(deltaFile)).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(deltaFile.actionNamed("EgressAction1")).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.actionNamed("EgressAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
    }

    @Test
    void testAdvanceCompleteEgressAction_allComplete() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, "EnrichAction1", "EnrichAction2", "FormatAction1", "FormatAction2", "ValidateAction1", "ValidateAction2", "EgressAction1", "EgressAction2");

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
        deltaFile.queueNewAction("ErrorEnrichAction");
        deltaFile.errorAction(ActionEventInput.newBuilder()
                .did(deltaFile.getDid())
                .action("ErrorEnrichAction")
                .error(ErrorInput.newBuilder()
                        .context("context")
                        .cause("cause")
                        .build())
                .build());

        Mockito.when(enrichFlowService.getRunningFlows()).thenReturn(Collections.emptyList());
        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(Collections.emptyList());
        assertThat(stateMachine.advance(deltaFile)).isEmpty();
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.ERROR);
    }

    @Test
    void testNoEgressFlowRequiredForSplitLoadActions() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.ENRICH);
        deltaFile.queueNewAction("SplitLoadAction");
        deltaFile.splitAction(ActionEventInput.newBuilder()
                .did(deltaFile.getDid())
                .action("SplitLoadAction")
                .build());

        Mockito.when(enrichFlowService.getRunningFlows()).thenReturn(Collections.emptyList());
        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(Collections.emptyList());
        assertThat(stateMachine.advance(deltaFile)).isEmpty();
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
    }

    @Test
    void testNoEgressFlowRequiredForFilteredLoadActions() throws MissingEgressFlowException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.setStage(DeltaFileStage.ENRICH);
        deltaFile.queueNewAction("FilteredLoadAction");
        deltaFile.filterAction(ActionEventInput.newBuilder()
                .did(deltaFile.getDid())
                .action("FilteredLoadAction")
                .build(), "filterd");

        Mockito.when(enrichFlowService.getRunningFlows()).thenReturn(Collections.emptyList());
        Mockito.when(egressFlowService.getMatchingFlows(INGRESS_FLOW)).thenReturn(Collections.emptyList());
        assertThat(stateMachine.advance(deltaFile)).isEmpty();
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
    }

    private void addCompletedActions(DeltaFile deltaFile, String... actions) {
        for (String action : actions) {
            deltaFile.queueAction(action);
            deltaFile.completeAction(action, null, null);
        }
    }

    private DeltaFile makeDomainAndEnrichFile() {
        return makeCustomFile(false, false);
    }

    private DeltaFile makeDeltaFile() {
        return makeCustomFile(true, true);
    }

    private DeltaFile makeCustomFile(boolean withSourceInfo, boolean withProtocolStack) {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", INGRESS_FLOW);
        deltaFile.addDomain(DOMAIN, "value", MediaType.ALL_VALUE);
        deltaFile.addEnrichment(ENRICH, "value", MediaType.ALL_VALUE);

        if (withSourceInfo) {
            deltaFile.setSourceInfo(new SourceInfo("input.txt", "sample", List.of(new KeyValue(SOURCE_KEY, "value"))));
        }

        if (withProtocolStack) {
            Content content = Content.newBuilder().contentReference(new ContentReference("objectName", 0, 500, "did", "" + "application/octet-stream")).build();
            deltaFile.getProtocolStack().add(new ProtocolLayer(INGRESS_ACTION,
                    List.of(content),
                    Collections.singletonList(new KeyValue(PROTOCOL_LAYER_KEY, "value"))));
        }
        return deltaFile;
    }

    @Builder
    private static class EgressFlowMaker {
        @Singular
        List<String> formatRequiresDomains;
        @Singular("formatRequiresEnrichment")
        List<String> formatRequiresEnrichment;
        @Builder.Default
        String formatActionName = FORMAT_ACTION;
        @Builder.Default
        List<String> validateActions = List.of(VALIDATE_ACTION);
        @Builder.Default
        String egressActionName = EGRESS_ACTION;
        @Builder.Default
        String name = EGRESS_FLOW;

        private EgressFlow makeEgressFlow() {
            EgressFlow egressFlow = new EgressFlow();
            egressFlow.setName(name);
            EgressActionConfiguration egressActionConfiguration = new EgressActionConfiguration();
            egressActionConfiguration.setName(egressActionName);
            egressFlow.setEgressAction(egressActionConfiguration);

            FormatActionConfiguration formatActionConfiguration = new FormatActionConfiguration();
            formatActionConfiguration.setName(formatActionName);
            formatActionConfiguration.setRequiresEnrichments(formatRequiresEnrichment);
            formatActionConfiguration.setRequiresDomains(formatRequiresDomains);
            egressFlow.setFormatAction(formatActionConfiguration);
            egressFlow.setValidateActions(validateActions.stream().map(this::named).collect(Collectors.toList()));

            return egressFlow;
        }

        ValidateActionConfiguration named(String name) {
            ValidateActionConfiguration validateActionConfiguration = new ValidateActionConfiguration();
            validateActionConfiguration.setName(name);
            return validateActionConfiguration;
        }
    }

    @Builder
    private static class EnrichFlowMaker {
        @Singular
        List<String> enrichRequiresDomains;
        @Singular("enrichRequiresEnrichment")
        List<String> enrichRequiresEnrichment;
        @Singular("enrichRequiresMetadata")
        List<KeyValue> enrichRequiresMetadata;
        @Builder.Default
        String enrichActionName = ENRICH_ACTION;

        private EnrichFlow makeEnrichFlow() {
            EnrichFlow enrichFlow = new EnrichFlow();

            EnrichActionConfiguration enrich = new EnrichActionConfiguration();
            enrich.setName(enrichActionName);
            enrich.setRequiresDomains(enrichRequiresDomains);
            enrich.setRequiresEnrichments(enrichRequiresEnrichment);
            enrich.setRequiresMetadataKeyValues(enrichRequiresMetadata);
            enrichFlow.setEnrichActions(List.of(enrich));

            return enrichFlow;
        }
    }
}
