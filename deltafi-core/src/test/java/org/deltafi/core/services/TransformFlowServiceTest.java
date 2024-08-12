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

import org.deltafi.common.types.FlowType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.repo.TransformFlowRepo;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.snapshot.FlowSnapshot;
import org.deltafi.core.types.snapshot.TransformFlowSnapshot;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.TransformFlow;
import org.deltafi.core.types.TransformFlowPlanEntity;
import org.deltafi.core.validation.TransformFlowValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.core.datafetchers.FlowPlanDatafetcherTestHelper.PLUGIN_COORDINATES;

@ExtendWith(MockitoExtension.class)
class TransformFlowServiceTest {

    @Mock
    TransformFlowRepo transformFlowRepo;

    @Mock
    TransformFlowValidator flowValidator;

    @InjectMocks
    TransformFlowService transformFlowService;

    @Captor
    ArgumentCaptor<List<TransformFlow>> flowCaptor;

    @Test
    void buildFlow() {
        TransformFlow running = transformFlow("running", FlowState.RUNNING, true);
        TransformFlow stopped = transformFlow("stopped", FlowState.STOPPED, false);
        Mockito.when(transformFlowRepo.findByNameAndType("running", TransformFlow.class)).thenReturn(Optional.of(running));
        Mockito.when(transformFlowRepo.findByNameAndType("stopped", TransformFlow.class)).thenReturn(Optional.of(stopped));
        Mockito.when(flowValidator.validate(Mockito.any())).thenReturn(Collections.emptyList());

        TransformFlowPlanEntity runningFlowPlan = new TransformFlowPlanEntity("running", "yep", PLUGIN_COORDINATES);
        TransformFlowPlanEntity stoppedFlowPlan = new TransformFlowPlanEntity("stopped", "naw", PLUGIN_COORDINATES);

        TransformFlow runningTransformFlow = transformFlowService.buildFlow(runningFlowPlan, Collections.emptyList());
        TransformFlow stoppedTransformFlow = transformFlowService.buildFlow(stoppedFlowPlan, Collections.emptyList());

        assertThat(runningTransformFlow.isRunning()).isTrue();
        assertThat(runningTransformFlow.isTestMode()).isTrue();
        assertThat(stoppedTransformFlow.isRunning()).isFalse();
        assertThat(stoppedTransformFlow.isTestMode()).isFalse();
    }

    @Test
    void updateSnapshot() {
        List<TransformFlow> flows = new ArrayList<>();
        flows.add(transformFlow("a", FlowState.RUNNING, false));
        flows.add(transformFlow("b", FlowState.STOPPED, false));
        flows.add(transformFlow("c", FlowState.INVALID, true));

        Mockito.when(transformFlowRepo.findAllByType(TransformFlow.class)).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        transformFlowService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getTransformFlows()).hasSize(3);

        Map<String, TransformFlowSnapshot> transformFlowSnapshotMap = systemSnapshot.getTransformFlows().stream()
                .collect(Collectors.toMap(FlowSnapshot::getName, Function.identity()));

        TransformFlowSnapshot aFlowSnapshot = transformFlowSnapshotMap.get("a");
        assertThat(aFlowSnapshot.isRunning()).isTrue();
        assertThat(aFlowSnapshot.isTestMode()).isFalse();

        TransformFlowSnapshot bFlowSnapshot = transformFlowSnapshotMap.get("b");
        assertThat(bFlowSnapshot.isRunning()).isFalse();
        assertThat(bFlowSnapshot.isTestMode()).isFalse();

        TransformFlowSnapshot cFlowSnapshot = transformFlowSnapshotMap.get("c");
        assertThat(cFlowSnapshot.isRunning()).isFalse();
        assertThat(cFlowSnapshot.isTestMode()).isTrue();
    }

    @Test
    void testResetFromSnapshot() {
        TransformFlow running = transformFlow("running", FlowState.RUNNING, true);
        TransformFlow stopped = transformFlow("stopped", FlowState.STOPPED, false);
        TransformFlow invalid = transformFlow("invalid", FlowState.INVALID, false);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setTransformFlows(List.of(
                new TransformFlowSnapshot("running", true, false),
                new TransformFlowSnapshot("stopped", true, true),
                new TransformFlowSnapshot("invalid", true, false),
                new TransformFlowSnapshot("missing", true, true)));

        Mockito.when(transformFlowRepo.findAllByType(TransformFlow.class)).thenReturn(List.of(running, stopped, invalid));
        Mockito.when(transformFlowRepo.findByNameAndType("running", TransformFlow.class)).thenReturn(Optional.of(running));
        Mockito.when(transformFlowRepo.findByNameAndType("stopped", TransformFlow.class)).thenReturn(Optional.of(stopped));
        Mockito.when(transformFlowRepo.findByNameAndType("invalid", TransformFlow.class)).thenReturn(Optional.of(invalid));
        Mockito.when(transformFlowRepo.findByNameAndType("missing", TransformFlow.class)).thenReturn(Optional.empty());

        Result result = transformFlowService.resetFromSnapshot(systemSnapshot, true);

        // verify the hard reset stopped any running flows
        Mockito.verify(transformFlowRepo).updateFlowStatusState("running", FlowState.STOPPED, FlowType.TRANSFORM);

        Mockito.verify(transformFlowRepo).saveAll(flowCaptor.capture());

        Map<String, TransformFlow> updatedFlows = flowCaptor.getValue().stream()
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

    TransformFlow transformFlow(String name, FlowState flowState, boolean testMode) {
        TransformFlow transformFlow = new TransformFlow();
        transformFlow.setName(name);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        flowStatus.setTestMode(testMode);
        transformFlow.setFlowStatus(flowStatus);
        return transformFlow;
    }
}