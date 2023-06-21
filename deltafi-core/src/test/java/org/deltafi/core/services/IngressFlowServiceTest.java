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

import org.deltafi.common.types.IngressFlowPlan;
import org.deltafi.common.types.LoadActionConfiguration;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.generated.types.IngressFlowErrorState;
import org.deltafi.core.repo.IngressFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.IngressFlow;
import org.deltafi.core.types.Result;
import org.deltafi.core.util.FlowBuilders;
import org.deltafi.core.validation.IngressFlowValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class IngressFlowServiceTest {

    private static final List<String> RUNNING_FLOWS = List.of("a", "b");
    private static final List<String> TEST_FLOWS = List.of("a", "b");

    @Mock
    IngressFlowRepo ingressFlowRepo;

    @Mock
    IngressFlowValidator flowValidator;

    @Mock
    ErrorCountService errorCountService;

    @InjectMocks
    IngressFlowService ingressFlowService;

    @Test
    void buildFlow() {
        IngressFlow running = ingressFlow("running", FlowState.RUNNING, true);
        running.setMaxErrors(3);
        IngressFlow stopped = ingressFlow("stopped", FlowState.STOPPED, false);
        Mockito.when(ingressFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(ingressFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(flowValidator.validate(Mockito.any())).thenReturn(Collections.emptyList());

        IngressFlowPlan runningFlowPlan = new IngressFlowPlan("running", "yep");
        runningFlowPlan.setLoadAction(new LoadActionConfiguration("LoadActionConfig", "LoadActionConfigType"));
        IngressFlowPlan stoppedFlowPlan = new IngressFlowPlan("stopped", "naw");
        stoppedFlowPlan.setLoadAction(new LoadActionConfiguration("LoadActionConfig", "LoadActionConfigType"));

        IngressFlow runningIngressFlow = ingressFlowService.buildFlow(runningFlowPlan, Collections.emptyList());
        IngressFlow stoppedIngressFlow = ingressFlowService.buildFlow(stoppedFlowPlan, Collections.emptyList());

        assertThat(runningIngressFlow.isRunning()).isTrue();
        assertThat(runningIngressFlow.isTestMode()).isTrue();
        assertThat(runningIngressFlow.getMaxErrors()).isEqualTo(3);
        assertThat(stoppedIngressFlow.isRunning()).isFalse();
        assertThat(stoppedIngressFlow.isTestMode()).isFalse();
    }

    @Test
    void updateSnapshot() {
        List<IngressFlow> flows = RUNNING_FLOWS.stream().map(this::runningFlow).collect(Collectors.toList());

        flows.add(ingressFlow("c", FlowState.STOPPED, false));
        Mockito.when(ingressFlowRepo.findAll()).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        ingressFlowService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getRunningIngressFlows()).isEqualTo(RUNNING_FLOWS);
        assertThat(systemSnapshot.getTestIngressFlows()).isEqualTo(TEST_FLOWS);
    }

    @Test
    void testResetFromSnapshot() {
        IngressFlow running = ingressFlow("running", FlowState.RUNNING, true);
        IngressFlow stopped = ingressFlow("stopped", FlowState.STOPPED, false);
        IngressFlow invalid = ingressFlow("invalid", FlowState.INVALID, false);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setRunningIngressFlows(List.of("running", "stopped", "invalid", "missing"));
        systemSnapshot.setTestIngressFlows(List.of("stopped", "missing"));

        Mockito.when(ingressFlowRepo.findAll()).thenReturn(List.of(running, stopped, invalid));
        Mockito.when(ingressFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(ingressFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(ingressFlowRepo.findById("invalid")).thenReturn(Optional.of(invalid));
        Mockito.when(ingressFlowRepo.findById("missing")).thenReturn(Optional.empty());

        Result result = ingressFlowService.resetFromSnapshot(systemSnapshot, true);

        // verify the hard reset stopped any running flows
        Mockito.verify(ingressFlowRepo).updateFlowState("running", FlowState.STOPPED);
        // stopped flow should be restarted since it was marked as running in the snapshot
        Mockito.verify(ingressFlowRepo).updateFlowState("stopped", FlowState.RUNNING);

        Mockito.verify(ingressFlowRepo).updateFlowTestMode("stopped", true);


        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getInfo()).isEmpty();
        assertThat(result.getErrors()).hasSize(3)
                .contains("Flow: missing is no longer installed and cannot be started")
                .contains("Flow: invalid is invalid and cannot be started")
                .contains("Flow: missing is no longer installed and cannot be set to test mode");
    }

    @Test
    void getRunningFromSnapshot() {
        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setRunningIngressFlows(List.of("a", "b"));
        systemSnapshot.setTestIngressFlows(List.of("c", "d"));
        assertThat(ingressFlowService.getRunningFromSnapshot(systemSnapshot)).isEqualTo(RUNNING_FLOWS);
        assertThat(ingressFlowService.getTestModeFromSnapshot(systemSnapshot)).isEqualTo(List.of("c", "d"));
    }

    @Test
    void testIngressFlowErrorsExceeded() {
        setupErrorExceeded();
        List<IngressFlowErrorState> errorStates = ingressFlowService.ingressFlowErrorsExceeded();
        assertEquals(2, errorStates.size());
        assertThat(errorStates.contains(new IngressFlowErrorState("flow3", 6, 5))).isTrue();
        assertThat(errorStates.contains(new IngressFlowErrorState("flow1", 1, 0))).isTrue();
    }

    @Test
    void testFlowErrorsExceeded() {
        setupErrorExceeded();
        Set<String> errorsExceeded = ingressFlowService.flowErrorsExceeded();
        assertEquals(2, errorsExceeded.size());
        assertThat(errorsExceeded.contains("flow1")).isTrue();
        assertThat(errorsExceeded.contains("flow3")).isTrue();
    }

    @Test
    void testUpgradeFlows() {
        PluginCoordinates coordinates = PluginCoordinates.builder().groupId("group").artifactId("artId").version("1.0.0").build();
        IngressFlow a = FlowBuilders.buildIngressFlow("a", coordinates);
        IngressFlow b = FlowBuilders.buildIngressFlow("b", coordinates);
        IngressFlow c = FlowBuilders.buildIngressFlow("c", coordinates);

        Mockito.when(ingressFlowRepo.findByGroupIdAndArtifactId("group", "artId"))
                .thenReturn(List.of(a, b, c));

        ingressFlowService.upgradeFlows(coordinates, List.of(), Set.of("a", "b"));
        Mockito.verify(ingressFlowRepo).saveAll(Mockito.eq(List.of()));
        Mockito.verify(ingressFlowRepo).deleteAllById(Set.of("c"));
    }

    void setupErrorExceeded() {
        IngressFlow flow1 = ingressFlow("flow1", FlowState.RUNNING, false);
        flow1.setMaxErrors(0);
        IngressFlow flow2 = ingressFlow("flow2", FlowState.RUNNING, false);
        flow2.setMaxErrors(5);
        IngressFlow flow3 = ingressFlow("flow3", FlowState.RUNNING, false);
        flow3.setMaxErrors(5);
        IngressFlow flow4 = ingressFlow("flow4", FlowState.STOPPED, false);
        flow4.setMaxErrors(5);

        Mockito.when(ingressFlowRepo.findAll()).thenReturn(List.of(flow1, flow2, flow3, flow4));
        Mockito.when(errorCountService.errorsForFlow("flow1")).thenReturn(1);
        Mockito.when(errorCountService.errorsForFlow("flow2")).thenReturn(5);
        Mockito.when(errorCountService.errorsForFlow("flow3")).thenReturn(6);

        ingressFlowService.refreshCache();
    }

    IngressFlow runningFlow(String name) {
        return ingressFlow(name, FlowState.RUNNING, true);
    }

    IngressFlow ingressFlow(String name, FlowState flowState, boolean testMode) {
        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName(name);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        flowStatus.setTestMode(testMode);
        ingressFlow.setFlowStatus(flowStatus);
        ingressFlow.setSchemaVersion(IngressFlow.CURRENT_SCHEMA_VERSION);
        return ingressFlow;
    }
}