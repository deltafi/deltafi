/**
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
import org.deltafi.core.types.EnrichFlow;
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
class EnrichFlowServiceTest {

    private static final List<String> RUNNING_FLOWS = List.of("a", "b");
    private static final List<String> TEST_FLOWS = List.of();

    @InjectMocks
    EnrichFlowService enrichFlowService;

    @Mock
    EnrichFlowRepo enrichFlowRepo;

    @Test
    void updateSnapshot() {
        List<EnrichFlow> flows = RUNNING_FLOWS.stream().map(this::runningFlow).collect(Collectors.toList());

        flows.add(enrichFlow("c", FlowState.STOPPED));
        Mockito.when(enrichFlowRepo.findAll()).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        enrichFlowService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getRunningEnrichFlows()).isEqualTo(RUNNING_FLOWS);
    }

    @Test
    void getRunningFromSnapshot() {
        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setRunningEnrichFlows(List.of("a", "b"));
        assertThat(enrichFlowService.getRunningFromSnapshot(systemSnapshot)).isEqualTo(RUNNING_FLOWS);
        assertThat(enrichFlowService.getTestModeFromSnapshot(systemSnapshot)).isEqualTo(TEST_FLOWS);
    }

    EnrichFlow runningFlow(String name) {
        return enrichFlow(name, FlowState.RUNNING);
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