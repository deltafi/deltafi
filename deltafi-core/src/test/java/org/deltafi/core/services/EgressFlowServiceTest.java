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

import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.repo.EgressFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.EgressFlowSnapshot;
import org.deltafi.core.snapshot.types.FlowSnapshot;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.Result;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EgressFlowServiceTest {

    @InjectMocks
    EgressFlowService egressFlowService;

    @Mock
    EgressFlowRepo egressFlowRepo;

    @Mock
    BuildProperties buildProperties;

    @Captor
    ArgumentCaptor<List<EgressFlow>> flowCaptor;

    @Test
    void updateSnapshot() {
        List<EgressFlow> flows = new ArrayList<>();
        flows.add(egressFlow("a", FlowState.RUNNING, false, null));
        flows.add(egressFlow("b", FlowState.STOPPED, false, Set.of("a", "b")));
        flows.add(egressFlow("c", FlowState.STOPPED, true, Set.of()));

        Mockito.when(egressFlowRepo.findAll()).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        egressFlowService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getEgressFlows()).hasSize(3);

        Map<String, EgressFlowSnapshot> egressFlowSnapshotMap = systemSnapshot.getEgressFlows().stream()
                .collect(Collectors.toMap(FlowSnapshot::getName, Function.identity()));

        EgressFlowSnapshot aFlowSnapshot = egressFlowSnapshotMap.get("a");
        assertThat(aFlowSnapshot.isRunning()).isTrue();
        assertThat(aFlowSnapshot.isTestMode()).isFalse();
        assertThat(aFlowSnapshot.getExpectedAnnotations()).isNull();

        EgressFlowSnapshot bFlowSnapshot = egressFlowSnapshotMap.get("b");
        assertThat(bFlowSnapshot.isRunning()).isFalse();
        assertThat(bFlowSnapshot.isTestMode()).isFalse();
        assertThat(bFlowSnapshot.getExpectedAnnotations()).hasSize(2).contains("a", "b");

        EgressFlowSnapshot cFlowSnapshot = egressFlowSnapshotMap.get("c");
        assertThat(cFlowSnapshot.isRunning()).isFalse();
        assertThat(cFlowSnapshot.isTestMode()).isTrue();
        assertThat(cFlowSnapshot.getExpectedAnnotations()).isEmpty();

        assertThat(systemSnapshot.getRunningEgressFlows()).isNull();
        assertThat(systemSnapshot.getTestEgressFlows()).isNull();
    }

    @Test
    void testResetFromSnapshot() {
        EgressFlow running = egressFlow("running", FlowState.RUNNING, true, Set.of());
        EgressFlow stopped = egressFlow("stopped", FlowState.STOPPED, false, Set.of());
        EgressFlow invalid = egressFlow("invalid", FlowState.INVALID, false, Set.of());

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setRunningEgressFlows(List.of("running", "stopped", "invalid", "missing"));
        systemSnapshot.setTestEgressFlows(List.of("stopped", "missing"));

        Mockito.when(egressFlowRepo.findAll()).thenReturn(List.of(running, stopped, invalid));
        Mockito.when(egressFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(egressFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(egressFlowRepo.findById("invalid")).thenReturn(Optional.of(invalid));
        Mockito.when(egressFlowRepo.findById("missing")).thenReturn(Optional.empty());

        Result result = egressFlowService.resetFromSnapshot(systemSnapshot, true);

        // verify the hard reset stopped any running flows
        Mockito.verify(egressFlowRepo).updateFlowState("running", FlowState.STOPPED);

        Mockito.verify(egressFlowRepo).saveAll(flowCaptor.capture());

        Map<String, EgressFlow> updatedFlows = flowCaptor.getValue().stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));

        // running is already running/testMode so no updates are made
        assertThat(updatedFlows).doesNotContainKey("running");

        // stopped flow should be restarted since it was marked as running in the snapshot, it should also be in test mode
        assertThat(updatedFlows.get("stopped").isRunning()).isTrue();
        assertThat(updatedFlows.get("stopped").isTestMode()).isTrue();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getInfo()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .contains("Flow missing is no longer installed")
                .contains("Flow: invalid is invalid and cannot be started");
    }

    EgressFlow egressFlow(String name, FlowState flowState, boolean testMode, Set<String> expectedAnnotations) {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setName(name);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        flowStatus.setTestMode(testMode);
        egressFlow.setFlowStatus(flowStatus);
        egressFlow.setExpectedAnnotations(expectedAnnotations);
        return egressFlow;
    }
}