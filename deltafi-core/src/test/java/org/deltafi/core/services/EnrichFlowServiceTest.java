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
import org.deltafi.core.repo.EnrichFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.EnrichFlowSnapshot;
import org.deltafi.core.snapshot.types.FlowSnapshot;
import org.deltafi.core.types.EnrichFlow;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EnrichFlowServiceTest {

    @InjectMocks
    EnrichFlowService enrichFlowService;

    @Mock
    EnrichFlowRepo enrichFlowRepo;

    @Captor
    ArgumentCaptor<List<EnrichFlow>> flowCaptor;

    @Test
    void updateSnapshot() {
        List<EnrichFlow> flows = List.of(enrichFlow("a", FlowState.RUNNING), enrichFlow("b", FlowState.STOPPED));
        Mockito.when(enrichFlowRepo.findAll()).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        enrichFlowService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getEnrichFlows()).hasSize(2);

        Map<String, EnrichFlowSnapshot> enrichFlowSnapshotMap = systemSnapshot.getEnrichFlows().stream()
                .collect(Collectors.toMap(FlowSnapshot::getName, Function.identity()));

        EnrichFlowSnapshot aFlowSnapshot = enrichFlowSnapshotMap.get("a");
        assertThat(aFlowSnapshot.isRunning()).isTrue();
        assertThat(aFlowSnapshot.isTestMode()).isFalse();

        EnrichFlowSnapshot bFlowSnapshot = enrichFlowSnapshotMap.get("b");
        assertThat(bFlowSnapshot.isRunning()).isFalse();

        assertThat(systemSnapshot.getRunningEnrichFlows()).isNull();
    }

    @Test
    void testResetFromSnapshot() {
        // TODO - verify old fields can be used to reset
        EnrichFlow running = enrichFlow("running", FlowState.RUNNING);
        EnrichFlow stopped = enrichFlow("stopped", FlowState.STOPPED);
        EnrichFlow invalid = enrichFlow("invalid", FlowState.INVALID);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setRunningEnrichFlows(List.of("running", "stopped", "invalid", "missing"));

        Mockito.when(enrichFlowRepo.findAll()).thenReturn(List.of(running, stopped, invalid));
        Mockito.when(enrichFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(enrichFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(enrichFlowRepo.findById("invalid")).thenReturn(Optional.of(invalid));
        Mockito.when(enrichFlowRepo.findById("missing")).thenReturn(Optional.empty());

        Result result = enrichFlowService.resetFromSnapshot(systemSnapshot, true);

        // verify the hard reset stopped any running flows
        Mockito.verify(enrichFlowRepo).updateFlowState("running", FlowState.STOPPED);

        Mockito.verify(enrichFlowRepo).saveAll(flowCaptor.capture());

        Map<String, EnrichFlow> updatedFlows = flowCaptor.getValue().stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));

        // running is already running/testMode so no updates are made
        assertThat(updatedFlows).doesNotContainKey("running");

        // stopped flow should be restarted since it was marked as running in the snapshot
        assertThat(updatedFlows.get("stopped").isRunning()).isTrue();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getInfo()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .contains("Flow missing is no longer installed")
                .contains("Flow: invalid is invalid and cannot be started");
    }

    EnrichFlow enrichFlow(String name, FlowState flowState) {
        EnrichFlow enrichFlow = new EnrichFlow();
        enrichFlow.setName(name);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        enrichFlow.setFlowStatus(flowStatus);
        return enrichFlow;
    }
}