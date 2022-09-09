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

import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.FlowStatus;
import org.deltafi.core.domain.repo.IngressFlowRepo;
import org.deltafi.core.domain.snapshot.SystemSnapshot;
import org.deltafi.core.domain.types.IngressFlow;
import org.deltafi.core.domain.types.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class IngressFlowServiceTest {

    private static final List<String> RUNNING_FLOWS = List.of("a", "b");

    @InjectMocks
    IngressFlowService ingressFlowService;

    @Mock
    IngressFlowRepo ingressFlowRepo;

    @Test
    void updateSnapshot() {
        List<IngressFlow> flows = RUNNING_FLOWS.stream().map(this::runningFlow).collect(Collectors.toList());

        flows.add(ingressFlow("c", FlowState.STOPPED));
        Mockito.when(ingressFlowRepo.findAll()).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        ingressFlowService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getRunningIngressFlows()).isEqualTo(RUNNING_FLOWS);
    }

    @Test
    void testResetFromSnapshot() {
        IngressFlow running = ingressFlow("running", FlowState.RUNNING);
        IngressFlow stopped = ingressFlow("stopped", FlowState.STOPPED);
        IngressFlow invalid = ingressFlow("invalid", FlowState.INVALID);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setRunningIngressFlows(List.of("running", "stopped", "invalid", "missing"));

        Mockito.when(ingressFlowRepo.findAll()).thenReturn(List.of(running, stopped, invalid));
        Mockito.when(ingressFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(ingressFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(ingressFlowRepo.findById("invalid")).thenReturn(Optional.of(invalid));

        Result result = ingressFlowService.resetFromSnapshot(systemSnapshot, true);

        // verify the hard reset stopped any running flows
        Mockito.verify(ingressFlowRepo).updateFlowState("running", FlowState.STOPPED);
        // stopped flow should be restarted since it was marked as running in the snapshot
        Mockito.verify(ingressFlowRepo).updateFlowState("stopped", FlowState.RUNNING);


        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getInfo()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .contains("Flow: missing is no longer installed")
                .contains("Flow: invalid is invalid and cannot be started");
    }

    @Test
    void getRunningFromSnapshot() {
        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setRunningIngressFlows(List.of("a", "b"));
        assertThat(ingressFlowService.getRunningFromSnapshot(systemSnapshot)).isEqualTo(RUNNING_FLOWS);
    }

    IngressFlow runningFlow(String name) {
        return ingressFlow(name, FlowState.RUNNING);
    }

    IngressFlow ingressFlow(String name, FlowState flowState) {
        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName(name);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        ingressFlow.setFlowStatus(flowStatus);
        return ingressFlow;
    }
}