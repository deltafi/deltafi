/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.common.types.FlowType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.repo.EgressFlowRepo;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.snapshot.EgressFlowSnapshot;
import org.deltafi.core.types.snapshot.FlowSnapshot;
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

    @Captor
    ArgumentCaptor<List<EgressFlow>> flowCaptor;

    @Mock
    FlowCacheService flowCacheService;

    @Test
    void updateSnapshot() {
        List<Flow> flows = new ArrayList<>();
        flows.add(egressFlow("a", FlowState.RUNNING, false, null));
        flows.add(egressFlow("b", FlowState.STOPPED, false, Set.of("a", "b")));
        flows.add(egressFlow("c", FlowState.STOPPED, true, Set.of()));

        Mockito.when(flowCacheService.flowsOfType(FlowType.EGRESS)).thenReturn(flows);

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
    }

    @Test
    void testResetFromSnapshot() {
        EgressFlow running = egressFlow("running", FlowState.RUNNING, true, Set.of());
        EgressFlow stopped = egressFlow("stopped", FlowState.STOPPED, false, Set.of());
        EgressFlow invalid = egressFlow("invalid", FlowState.INVALID, false, Set.of());

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setEgressFlows(List.of(
                new EgressFlowSnapshot("running", true, false),
                new EgressFlowSnapshot("stopped", true, true),
                new EgressFlowSnapshot("invalid", true, false),
                new EgressFlowSnapshot("missing", true, true)));

        Mockito.when(flowCacheService.flowsOfType(FlowType.EGRESS)).thenReturn(List.of(running, stopped, invalid));
        Mockito.when(flowCacheService.getFlowOrThrow(FlowType.EGRESS, "running")).thenReturn(running);
        Mockito.when(egressFlowRepo.findByNameAndType("running", EgressFlow.class)).thenReturn(Optional.of(running));
        Mockito.when(egressFlowRepo.findByNameAndType("stopped", EgressFlow.class)).thenReturn(Optional.of(stopped));
        Mockito.when(egressFlowRepo.findByNameAndType("invalid", EgressFlow.class)).thenReturn(Optional.of(invalid));
        Mockito.when(egressFlowRepo.findByNameAndType("missing", EgressFlow.class)).thenReturn(Optional.empty());

        Result result = egressFlowService.resetFromSnapshot(systemSnapshot, true);

        // verify the hard reset stopped any running flows
        Mockito.verify(egressFlowRepo).updateFlowStatusState("running", FlowState.STOPPED, FlowType.EGRESS);

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