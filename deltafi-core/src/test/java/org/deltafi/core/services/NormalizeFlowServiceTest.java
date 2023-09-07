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

import org.deltafi.common.types.NormalizeFlowPlan;
import org.deltafi.common.types.LoadActionConfiguration;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.generated.types.IngressFlowErrorState;
import org.deltafi.core.repo.NormalizeFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.FlowSnapshot;
import org.deltafi.core.snapshot.types.NormalizeFlowSnapshot;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.NormalizeFlow;
import org.deltafi.core.types.Result;
import org.deltafi.core.util.FlowBuilders;
import org.deltafi.core.validation.NormalizeFlowValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class NormalizeFlowServiceTest {

    @Mock
    NormalizeFlowRepo normalizeFlowRepo;

    @Mock
    NormalizeFlowValidator flowValidator;

    @Mock
    ErrorCountService errorCountService;

    @Mock
    BuildProperties buildProperties;

    @InjectMocks
    NormalizeFlowService normalizeFlowService;

    @Captor
    ArgumentCaptor<List<NormalizeFlow>> flowCaptor;

    @Test
    void buildFlow() {
        NormalizeFlow running = normalizeFlow("running", FlowState.RUNNING, true, 3);
        NormalizeFlow stopped = normalizeFlow("stopped", FlowState.STOPPED, false, -1);
        Mockito.when(normalizeFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(normalizeFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(flowValidator.validate(Mockito.any())).thenReturn(Collections.emptyList());

        NormalizeFlowPlan runningFlowPlan = new NormalizeFlowPlan("running", "yep");
        runningFlowPlan.setLoadAction(new LoadActionConfiguration("LoadActionConfig", "LoadActionConfigType"));
        NormalizeFlowPlan stoppedFlowPlan = new NormalizeFlowPlan("stopped", "naw");
        stoppedFlowPlan.setLoadAction(new LoadActionConfiguration("LoadActionConfig", "LoadActionConfigType"));

        NormalizeFlow runningNormalizeFlow = normalizeFlowService.buildFlow(runningFlowPlan, Collections.emptyList());
        NormalizeFlow stoppedNormalizeFlow = normalizeFlowService.buildFlow(stoppedFlowPlan, Collections.emptyList());

        assertThat(runningNormalizeFlow.isRunning()).isTrue();
        assertThat(runningNormalizeFlow.isTestMode()).isTrue();
        assertThat(runningNormalizeFlow.getMaxErrors()).isEqualTo(3);
        assertThat(stoppedNormalizeFlow.isRunning()).isFalse();
        assertThat(stoppedNormalizeFlow.isTestMode()).isFalse();
    }

    @Test
    void updateSnapshot() {
        // TODO - original tests should pass
        List<NormalizeFlow> flows = new ArrayList<>();
        flows.add(normalizeFlow("a", FlowState.RUNNING, false, -1));
        flows.add(normalizeFlow("b", FlowState.STOPPED, false, 1));
        flows.add(normalizeFlow("c", FlowState.INVALID, true, 1));
        Mockito.when(normalizeFlowRepo.findAll()).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        normalizeFlowService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getNormalizeFlows()).hasSize(3);

        Map<String, NormalizeFlowSnapshot> normalizeFlowSnapshotMap = systemSnapshot.getNormalizeFlows().stream()
                .collect(Collectors.toMap(FlowSnapshot::getName, Function.identity()));

        NormalizeFlowSnapshot aFlowSnapshot = normalizeFlowSnapshotMap.get("a");
        assertThat(aFlowSnapshot.isRunning()).isTrue();
        assertThat(aFlowSnapshot.isTestMode()).isFalse();
        assertThat(aFlowSnapshot.getMaxErrors()).isEqualTo(-1);

        NormalizeFlowSnapshot bFlowSnapshot = normalizeFlowSnapshotMap.get("b");
        assertThat(bFlowSnapshot.isRunning()).isFalse();
        assertThat(bFlowSnapshot.isTestMode()).isFalse();
        assertThat(bFlowSnapshot.getMaxErrors()).isEqualTo(1);

        NormalizeFlowSnapshot cFlowSnapshot = normalizeFlowSnapshotMap.get("c");
        assertThat(cFlowSnapshot.isRunning()).isFalse();
        assertThat(cFlowSnapshot.isTestMode()).isTrue();
        assertThat(cFlowSnapshot.getMaxErrors()).isEqualTo(1);

        assertThat(systemSnapshot.getRunningIngressFlows()).isNull();
        assertThat(systemSnapshot.getTestIngressFlows()).isNull();
    }

    @Test
    void testResetFromSnapshot() {
        NormalizeFlow running = normalizeFlow("running", FlowState.RUNNING, true, -1);
        NormalizeFlow stopped = normalizeFlow("stopped", FlowState.STOPPED, false, 1);
        NormalizeFlow invalid = normalizeFlow("invalid", FlowState.INVALID, false, 2);
        NormalizeFlow testModeMaxErrorsChanged = normalizeFlow("testModeMaxErrorsChanged", FlowState.STOPPED, false, 3);

        SystemSnapshot systemSnapshot = new SystemSnapshot();

        // create snapshot objects
        List<NormalizeFlowSnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot("running", true, false, 1));
        snapshots.add(snapshot("stopped", true, true, 2));
        snapshots.add(snapshot("invalid", true, false, -1));
        snapshots.add(snapshot("testModeMaxErrorsChanged", false, true, -1));
        snapshots.add(snapshot("missing", false, true, 1));
        systemSnapshot.setNormalizeFlows(snapshots);

        Mockito.when(normalizeFlowRepo.findAll()).thenReturn(List.of(running, stopped, invalid, testModeMaxErrorsChanged));
        Mockito.when(normalizeFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(normalizeFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(normalizeFlowRepo.findById("invalid")).thenReturn(Optional.of(invalid));
        Mockito.when(normalizeFlowRepo.findById("missing")).thenReturn(Optional.empty());
        Mockito.when(normalizeFlowRepo.findById("testModeMaxErrorsChanged")).thenReturn(Optional.of(testModeMaxErrorsChanged));

        Result result = normalizeFlowService.resetFromSnapshot(systemSnapshot, true);

        // verify the hard reset stopped any running flows
        Mockito.verify(normalizeFlowRepo).updateFlowState("running", FlowState.STOPPED);
        Mockito.verify(normalizeFlowRepo).saveAll(flowCaptor.capture());

        Map<String, NormalizeFlow> updatedFlows = flowCaptor.getValue().stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));

        assertThat(updatedFlows.get("running").isRunning()).isTrue();
        assertThat(updatedFlows.get("running").getMaxErrors()).isEqualTo(1);
        // stopped flow should be restarted since it was marked as running in the snapshot, it should also be in test mode
        assertThat(updatedFlows.get("stopped").isRunning()).isTrue();
        assertThat(updatedFlows.get("stopped").isTestMode()).isTrue();
        assertThat(updatedFlows.get("stopped").getMaxErrors()).isEqualTo(2);

        assertThat(updatedFlows.get("testModeMaxErrorsChanged").getMaxErrors()).isEqualTo(-1);
        assertThat(updatedFlows.get("testModeMaxErrorsChanged").isTestMode()).isTrue();
        assertThat(updatedFlows.get("testModeMaxErrorsChanged").isRunning()).isFalse();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getInfo()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .contains("Flow missing is no longer installed")
                .contains("Flow: invalid is invalid and cannot be started");
    }

    @Test
    void testNormalizeFlowErrorsExceeded() {
        setupErrorExceeded();
        List<IngressFlowErrorState> errorStates = normalizeFlowService.ingressFlowErrorsExceeded();
        assertEquals(2, errorStates.size());
        assertThat(errorStates).contains(new IngressFlowErrorState("flow3", 6, 5))
                .contains(new IngressFlowErrorState("flow1", 1, 0));
    }

    @Test
    void testFlowErrorsExceeded() {
        setupErrorExceeded();
        Set<String> errorsExceeded = normalizeFlowService.flowErrorsExceeded();
        assertThat(errorsExceeded).hasSize(2).contains("flow1", "flow3");
    }

    @Test
    void testUpgradeFlows() {
        PluginCoordinates coordinates = PluginCoordinates.builder().groupId("group").artifactId("artId").version("1.0.0").build();
        NormalizeFlow a = FlowBuilders.buildNormalizeFlow("a", coordinates);
        NormalizeFlow b = FlowBuilders.buildNormalizeFlow("b", coordinates);
        NormalizeFlow c = FlowBuilders.buildNormalizeFlow("c", coordinates);

        Mockito.when(normalizeFlowRepo.findByGroupIdAndArtifactId("group", "artId"))
                .thenReturn(List.of(a, b, c));

        normalizeFlowService.upgradeFlows(coordinates, List.of(), Set.of("a", "b"));
        Mockito.verify(normalizeFlowRepo).saveAll(List.of());
        Mockito.verify(normalizeFlowRepo).deleteAllById(Set.of("c"));
    }

    void setupErrorExceeded() {
        NormalizeFlow flow1 = normalizeFlow("flow1", FlowState.RUNNING, false, 0);
        NormalizeFlow flow2 = normalizeFlow("flow2", FlowState.RUNNING, false, 5);
        NormalizeFlow flow3 = normalizeFlow("flow3", FlowState.RUNNING, false, 5);
        NormalizeFlow flow4 = normalizeFlow("flow4", FlowState.STOPPED, false, 5);

        Mockito.when(normalizeFlowRepo.findAll()).thenReturn(List.of(flow1, flow2, flow3, flow4));
        Mockito.when(errorCountService.errorsForFlow("flow1")).thenReturn(1);
        Mockito.when(errorCountService.errorsForFlow("flow2")).thenReturn(5);
        Mockito.when(errorCountService.errorsForFlow("flow3")).thenReturn(6);

        normalizeFlowService.refreshCache();
    }

    NormalizeFlow normalizeFlow(String name, FlowState flowState, boolean testMode, int maxErrors) {
        NormalizeFlow normalizeFlow = new NormalizeFlow();
        normalizeFlow.setName(name);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        flowStatus.setTestMode(testMode);
        normalizeFlow.setFlowStatus(flowStatus);
        normalizeFlow.setMaxErrors(maxErrors);
        normalizeFlow.setSchemaVersion(NormalizeFlow.CURRENT_SCHEMA_VERSION);
        return normalizeFlow;
    }

    NormalizeFlowSnapshot snapshot(String name, boolean running, boolean testMode, int maxErrors) {
        NormalizeFlowSnapshot snapshot = new NormalizeFlowSnapshot(name);
        snapshot.setRunning(running);
        snapshot.setTestMode(testMode);
        snapshot.setMaxErrors(maxErrors);
        return snapshot;
    }
}