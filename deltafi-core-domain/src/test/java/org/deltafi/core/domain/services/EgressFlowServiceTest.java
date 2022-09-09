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
import org.deltafi.core.domain.repo.EgressFlowRepo;
import org.deltafi.core.domain.snapshot.SystemSnapshot;
import org.deltafi.core.domain.types.EgressFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EgressFlowServiceTest {

    private static final List<String> RUNNING_FLOWS = List.of("a", "b");

    @InjectMocks
    EgressFlowService egressFlowService;

    @Mock
    EgressFlowRepo egressFlowRepo;

    @Test
    void updateSnapshot() {
        List<EgressFlow> flows = RUNNING_FLOWS.stream().map(this::runningFlow).collect(Collectors.toList());

        flows.add(egressFlow("c", FlowState.STOPPED));
        Mockito.when(egressFlowRepo.findAll()).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        egressFlowService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getRunningEgressFlows()).isEqualTo(RUNNING_FLOWS);
    }

    @Test
    void getRunningFromSnapshot() {
        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setRunningEgressFlows(List.of("a", "b"));
        assertThat(egressFlowService.getRunningFromSnapshot(systemSnapshot)).isEqualTo(RUNNING_FLOWS);
    }

    EgressFlow runningFlow(String name) {
        return egressFlow(name, FlowState.RUNNING);
    }

    EgressFlow egressFlow(String name, FlowState flowState) {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setName(name);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        egressFlow.setFlowStatus(flowStatus);
        return egressFlow;
    }
}