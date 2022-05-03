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
package org.deltafi.core.domain.services;

import lombok.Builder;
import lombok.Singular;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.configuration.EgressActionConfiguration;
import org.deltafi.core.domain.configuration.EnrichActionConfiguration;
import org.deltafi.core.domain.configuration.FormatActionConfiguration;
import org.deltafi.core.domain.configuration.ValidateActionConfiguration;
import org.deltafi.core.domain.generated.types.Action;
import org.deltafi.core.domain.generated.types.ActionState;
import org.deltafi.core.domain.generated.types.DeltaFileStage;
import org.deltafi.core.domain.types.EgressFlow;
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

@ExtendWith(MockitoExtension.class)
class StateMachineTest {

    private static final String SOURCE_KEY = "sourceKey";
    private static final String PROTOCOL_LAYER_KEY = "protocolLayerKey";
    private static final String DOMAIN = "domain";
    private static final String ENRICH = "enrich";

    @InjectMocks
    StateMachine stateMachine;

    @Mock
    EgressFlowService egressFlowService;

    @Mock
    @SuppressWarnings("unused")
    ZipkinService zipkinService;

    @Test
    void testGetEnrichActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "includedFlow");
        EgressFlow egressFlow = EgressFlowMaker.builder().formatRequiresEnrichment(List.of("block")).build().makeEgressFlow();

        List<ActionInput> actionInputs = stateMachine.advanceEgress(egressFlow, deltaFile);

        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals("EnrichAction"));
    }

    @Test
    void testGetEnrichActionsMatchesDomainAndEnrichment() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .formatRequiresEnrichment("block")
                .build().makeEgressFlow();

        List<ActionInput> actionInputs = stateMachine.advanceEgress(egressFlow, deltaFile);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals("EnrichAction"));
    }

    @Test
    void testGetEnrichActionsDomainDoesNotMatch() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .enrichRequiresDomain("otherDomain")
                .enrichRequiresEnrichment(ENRICH)
                .formatRequiresEnrichment("block")
                .build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsEnrichmentDoesNotMatch() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment("otherEnrich")
                .formatRequiresEnrichment("block")
                .build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsDoesNotMatchMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue("wrongKey", "value"))
                .formatRequiresEnrichment("block")
                .build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsNoMetadataAvailable() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(SOURCE_KEY, "value"))
                .formatRequiresEnrichment("block")
                .build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetEnrichActionsMatchesSourceInfoMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(SOURCE_KEY, "value"))
                .formatRequiresEnrichment("block")
                .build().makeEgressFlow();

        List<ActionInput> actionInputs = stateMachine.advanceEgress(egressFlow, deltaFile);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals("EnrichAction"));
    }

    @Test
    void testGetEnrichActionsMatchesProtocolLayerMetadata() {
        DeltaFile deltaFile = makeDeltaFile();

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .enrichRequiresDomain(DOMAIN)
                .enrichRequiresEnrichment(ENRICH)
                .enrichRequiresMetadata(new KeyValue(PROTOCOL_LAYER_KEY, "value"))
                .formatRequiresEnrichment("block")
                .build().makeEgressFlow();

        List<ActionInput> actionInputs = stateMachine.advanceEgress(egressFlow, deltaFile);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals("EnrichAction"));
    }

    @Test
    void testGetFormatActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "includedFlow");
        addCompletedActions(deltaFile, "EnrichAction");

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        List<ActionInput> actionInputs = stateMachine.advanceEgress(egressFlow, deltaFile);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals("FormatAction"));
    }

    @Test
    void testGetFormatActionsAlreadyComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "theFlow");
        addCompletedActions(deltaFile, "EnrichAction", "FormatAction", "ValidateAction");

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        List<ActionInput> actionInputs = stateMachine.advanceEgress(egressFlow, deltaFile);
        assertThat(actionInputs).hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals("TheEgressAction"));
    }

    @Test
    void testGetFormatActionsMatchesDomainAndEnrichment() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();
        addCompletedActions(deltaFile, "EnrichAction");

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain(DOMAIN)
                .formatRequiresEnrichment(ENRICH)
                .build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile))
                .hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals("FormatAction"));
    }

    @Test
    void testGetFormatActionsDomainDiffers() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();
        addCompletedActions(deltaFile, "EnrichAction");

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain("otherDomain")
                .formatRequiresEnrichment(ENRICH)
                .build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetFormatActionsEnrichDiffers() {
        DeltaFile deltaFile = makeDomainAndEnrichFile();
        addCompletedActions(deltaFile, "EnrichAction");

        EgressFlow egressFlow = EgressFlowMaker.builder()
                .formatRequiresDomain(DOMAIN)
                .formatRequiresEnrichment("otherEnrich")
                .build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetValidateActions() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "includedFlow");
        addCompletedActions(deltaFile, "EnrichAction", "FormatAction");

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile))
                .hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals("ValidateAction"));

    }

    @Test
    void testGetValidateActionsNoFormatErrorAllowed() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "includedFlow");
        addCompletedActions(deltaFile, "EnrichAction");

        deltaFile.queueAction("FormatAction");
        deltaFile.errorAction("FormatAction", null, null, "failed", "failed");

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile)).isEmpty();
    }

    @Test
    void testGetValidateActionsAlreadyComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "theFlow");
        addCompletedActions(deltaFile, "EnrichAction", "FormatAction", "ValidateAction");

        EgressFlow egressFlow = EgressFlowMaker.builder().build().makeEgressFlow();

        assertThat(stateMachine.advanceEgress(egressFlow, deltaFile))
                .hasSize(1).matches((list) -> list.get(0).getActionContext().getName().equals("TheEgressAction"));

    }

    @Test
    void testAdvanceToMultipleValidateAction() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS);
        addCompletedActions(deltaFile, "EnrichAction", "FormatAction");

        EgressFlow egressFlow = EgressFlowMaker.builder().validateActions(List.of("ValidateAction1", "ValidateAction2")).build().makeEgressFlow();
        Mockito.when(egressFlowService.getMatchingFlows("flow")).thenReturn(List.of(egressFlow));

        stateMachine.advance(deltaFile);

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(deltaFile.actionNamed("ValidateAction1")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
        assertThat(deltaFile.actionNamed("ValidateAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
    }

    @Test
    void testAdvanceCompleteValidateAction_onePending() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS);

        Action formatAction = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        Action completedAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction1").build();
        Action dispatchedAction = Action.newBuilder().state(ActionState.QUEUED).name("ValidateAction2").build();

        deltaFile.setActions(new ArrayList<>(List.of(formatAction, completedAction, dispatchedAction)));

        EgressFlow egressFlow = EgressFlowMaker.builder().validateActions(List.of("ValidateAction1", "ValidateAction2")).build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows("flow")).thenReturn(List.of(egressFlow));

        stateMachine.advance(deltaFile);

        // TODO - is this really testing anything?
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(completedAction.getState()).isEqualTo(ActionState.COMPLETE);
        assertThat(dispatchedAction.getState()).isEqualTo(ActionState.QUEUED);
    }

    @Test
    void testAdvanceCompleteValidateAction_allComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS);

        Action formatAction = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        Action completedAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction1").build();
        Action dispatchedAction = Action.newBuilder().state(ActionState.COMPLETE).name("ValidateAction2").build();

        deltaFile.setActions(new ArrayList<>(Arrays.asList(formatAction, completedAction, dispatchedAction)));

        EgressFlow egressFlow = EgressFlowMaker.builder().validateActions(List.of("ValidateAction1", "ValidateAction2")).build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows("flow")).thenReturn(List.of(egressFlow));

        stateMachine.advance(deltaFile);

        // TODO - is this really testing anything?
        assertThat(completedAction.getState()).isEqualTo(ActionState.COMPLETE);
        assertThat(dispatchedAction.getState()).isEqualTo(ActionState.COMPLETE);
        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
    }

    @Test
    void testAdvanceMultipleEgressFlows() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, "EnrichAction2");

        EgressFlow egressFlowAtEnrich = EgressFlowMaker.builder()
                .enrichActionName("EnrichAction1")
                .egressActionName("EgressAction1")
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .build().makeEgressFlow();

        EgressFlow egressFlowAtFormat = EgressFlowMaker.builder()
                .enrichActionName("EnrichAction2")
                .egressActionName("EgressAction2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows("flow")).thenReturn(List.of(egressFlowAtEnrich, egressFlowAtFormat));

        List<ActionInput> actionInputs = stateMachine.advance(deltaFile);
        assertThat(actionInputs).hasSize(2);

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(actionInputs.get(0)).matches(actionInput -> "EnrichAction1".equals(actionInput.getActionContext().getName()));
        assertThat(actionInputs.get(1)).matches(actionInput -> "FormatAction2".equals(actionInput.getActionContext().getName()));
    }

    @Test
    void testAdvanceToEgressAction() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, "EnrichAction1", "EnrichAction2", "FormatAction1", "FormatAction2", "ValidateAction1", "ValidateAction2");

        EgressFlow egress1 = EgressFlowMaker.builder()
                .enrichActionName("EnrichAction1")
                .egressActionName("EgressAction1")
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .build().makeEgressFlow();

        EgressFlow egress2 = EgressFlowMaker.builder()
                .enrichActionName("EnrichAction2")
                .egressActionName("EgressAction2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows("flow")).thenReturn(List.of(egress1, egress2));

        List<ActionInput> actionInputs = stateMachine.advance(deltaFile);
        assertThat(actionInputs).hasSize(2);

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(actionInputs.get(0)).matches(actionInput -> "EgressAction1".equals(actionInput.getActionContext().getName()));
        assertThat(actionInputs.get(1)).matches(actionInput -> "EgressAction2".equals(actionInput.getActionContext().getName()));
        assertThat(deltaFile.actionNamed("EgressAction1")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
        assertThat(deltaFile.actionNamed("EgressAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
    }

    @Test
    void testAdvanceCompleteEgressAction_onePending() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS);

        deltaFile.queueNewAction("EgressAction2");
        addCompletedActions(deltaFile, "EnrichAction1", "EnrichAction2", "FormatAction1", "FormatAction2", "ValidateAction1", "ValidateAction2", "EgressAction1");

        EgressFlow egress1 = EgressFlowMaker.builder()
                .enrichActionName("EnrichAction1")
                .egressActionName("EgressAction1")
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .build().makeEgressFlow();

        EgressFlow egress2 = EgressFlowMaker.builder()
                .enrichActionName("EnrichAction2")
                .egressActionName("EgressAction2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows("flow")).thenReturn(List.of(egress1, egress2));

        assertThat(stateMachine.advance(deltaFile)).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS);
        assertThat(deltaFile.actionNamed("EgressAction1")).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.actionNamed("EgressAction2")).isPresent().get().matches(action -> ActionState.QUEUED.equals(action.getState()));
    }

    @Test
    void testAdvanceCompleteEgressAction_allComplete() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setStage(DeltaFileStage.EGRESS);

        addCompletedActions(deltaFile, "EnrichAction1", "EnrichAction2", "FormatAction1", "FormatAction2", "ValidateAction1", "ValidateAction2", "EgressAction1", "EgressAction2");



        EgressFlow egress1 = EgressFlowMaker.builder()
                .enrichActionName("EnrichAction1")
                .egressActionName("EgressAction1")
                .formatActionName("FormatAction1")
                .validateActions(List.of("ValidateAction1"))
                .build().makeEgressFlow();

        EgressFlow egress2 = EgressFlowMaker.builder()
                .enrichActionName("EnrichAction2")
                .egressActionName("EgressAction2")
                .formatActionName("FormatAction2")
                .validateActions(List.of("ValidateAction2"))
                .build().makeEgressFlow();

        Mockito.when(egressFlowService.getMatchingFlows("flow")).thenReturn(List.of(egress1, egress2));

        assertThat(stateMachine.advance(deltaFile)).isEmpty();

        assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
        assertThat(deltaFile.actionNamed("EgressAction1")).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
        assertThat(deltaFile.actionNamed("EgressAction2")).isPresent().get().matches(action -> ActionState.COMPLETE.equals(action.getState()));
    }

    private void addCompletedActions(DeltaFile deltaFile, String ... actions) {
        for (String action : actions) {
            deltaFile.queueAction(action);
            deltaFile.completeAction(action, null, null);
        }
    }

    private DeltaFile makeDomainAndEnrichFile() {
        return makeCustomFile(false,  false);
    }

    private DeltaFile makeDeltaFile() {
        return makeCustomFile(true, true);
    }

    private DeltaFile makeCustomFile(boolean withSourceInfo, boolean withProtocolStack) {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "theFlow");
        deltaFile.addDomain(DOMAIN, "value", MediaType.ALL_VALUE);
        deltaFile.addEnrichment(ENRICH, "value", MediaType.ALL_VALUE);

        if (withSourceInfo) {
            deltaFile.setSourceInfo(new SourceInfo("input.txt", "sample", List.of(new KeyValue(SOURCE_KEY, "value"))));
        }

        if (withProtocolStack) {
            Content content = Content.newBuilder().contentReference(new ContentReference("objectName", 0, 500, "did", "" + "application/octet-stream")).build();
            deltaFile.getProtocolStack().add(new ProtocolLayer("json", INGRESS_ACTION,
                    List.of(content),
                    Collections.singletonList(new KeyValue(PROTOCOL_LAYER_KEY, "value"))));
        }
        return deltaFile;
    }

    @Builder
    private static class EgressFlowMaker {
        @Singular
        List<String> enrichRequiresDomains;
        @Singular("enrichRequiresEnrichment")
        List<String> enrichRequiresEnrichment;
        @Singular("enrichRequiresMetadata")
        List<KeyValue> enrichRequiresMetadata;
        @Singular
        List<String> formatRequiresDomains;
        @Singular("formatRequiresEnrichment")
        List<String> formatRequiresEnrichment;
        @Builder.Default
        String enrichActionName = "EnrichAction";
        @Builder.Default
        String formatActionName = "FormatAction";
        @Builder.Default
        List<String> validateActions= List.of("ValidateAction");
        @Builder.Default
        String egressActionName = "TheEgressAction";

        private EgressFlow makeEgressFlow() {
            EgressFlow egressFlow = new EgressFlow();

            EnrichActionConfiguration enrich = new EnrichActionConfiguration();
            enrich.setName(enrichActionName);
            enrich.setRequiresDomains(enrichRequiresDomains);
            enrich.setRequiresEnrichment(enrichRequiresEnrichment);
            enrich.setRequiresMetadataKeyValues(enrichRequiresMetadata);
            egressFlow.setEnrichActions(List.of(enrich));

            EgressActionConfiguration egressActionConfiguration = new EgressActionConfiguration();
            egressActionConfiguration.setName(egressActionName);
            egressFlow.setEgressAction(egressActionConfiguration);

            FormatActionConfiguration formatActionConfiguration = new FormatActionConfiguration();
            formatActionConfiguration.setName(formatActionName);
            formatActionConfiguration.setRequiresEnrichment(formatRequiresEnrichment);
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
}
