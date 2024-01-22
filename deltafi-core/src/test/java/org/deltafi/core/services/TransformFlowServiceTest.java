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

import org.deltafi.common.types.TransformFlowPlan;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.generated.types.IngressFlowErrorState;
import org.deltafi.core.repo.TransformFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.FlowSnapshot;
import org.deltafi.core.snapshot.types.TransformFlowSnapshot;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.TransformFlow;
import org.deltafi.core.validation.TransformFlowValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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
class TransformFlowServiceTest {

    @Mock
    TransformFlowRepo transformFlowRepo;

    @Mock
    TransformFlowValidator flowValidator;

    @Mock
    ErrorCountService errorCountService;

    @InjectMocks
    TransformFlowService transformFlowService;

    @Captor
    ArgumentCaptor<List<TransformFlow>> flowCaptor;

    @Test
    void buildFlow() {
        TransformFlow running = transformFlow("running", FlowState.RUNNING, true, 10);
        TransformFlow stopped = transformFlow("stopped", FlowState.STOPPED, false, -1);
        Mockito.when(transformFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(transformFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(flowValidator.validate(Mockito.any())).thenReturn(Collections.emptyList());

        TransformFlowPlan runningFlowPlan = new TransformFlowPlan("running", "yep");
        TransformFlowPlan stoppedFlowPlan = new TransformFlowPlan("stopped", "naw");

        TransformFlow runningTransformFlow = transformFlowService.buildFlow(runningFlowPlan, Collections.emptyList());
        TransformFlow stoppedTransformFlow = transformFlowService.buildFlow(stoppedFlowPlan, Collections.emptyList());

        assertThat(runningTransformFlow.isRunning()).isTrue();
        assertThat(runningTransformFlow.isTestMode()).isTrue();
        assertThat(runningTransformFlow.getMaxErrors()).isEqualTo(10);
        assertThat(stoppedTransformFlow.isRunning()).isFalse();
        assertThat(stoppedTransformFlow.isTestMode()).isFalse();
    }

    @Test
    void updateSnapshot() {
        List<TransformFlow> flows = new ArrayList<>();
        flows.add(transformFlow("a", FlowState.RUNNING, false, -1));
        flows.add(transformFlow("b", FlowState.STOPPED, false, 1));
        flows.add(transformFlow("c", FlowState.INVALID, true, 1));

        Mockito.when(transformFlowRepo.findAll()).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        transformFlowService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getTransformFlows()).hasSize(3);

        Map<String, TransformFlowSnapshot> transformFlowSnapshotMap = systemSnapshot.getTransformFlows().stream()
                .collect(Collectors.toMap(FlowSnapshot::getName, Function.identity()));

        TransformFlowSnapshot aFlowSnapshot = transformFlowSnapshotMap.get("a");
        assertThat(aFlowSnapshot.isRunning()).isTrue();
        assertThat(aFlowSnapshot.isTestMode()).isFalse();
        assertThat(aFlowSnapshot.getMaxErrors()).isEqualTo(-1);

        TransformFlowSnapshot bFlowSnapshot = transformFlowSnapshotMap.get("b");
        assertThat(bFlowSnapshot.isRunning()).isFalse();
        assertThat(bFlowSnapshot.isTestMode()).isFalse();
        assertThat(bFlowSnapshot.getMaxErrors()).isEqualTo(1);

        TransformFlowSnapshot cFlowSnapshot = transformFlowSnapshotMap.get("c");
        assertThat(cFlowSnapshot.isRunning()).isFalse();
        assertThat(cFlowSnapshot.isTestMode()).isTrue();
        assertThat(cFlowSnapshot.getMaxErrors()).isEqualTo(1);
    }

    @Test
    void testResetFromSnapshot() {
        TransformFlow running = transformFlow("running", FlowState.RUNNING, true, -1);
        TransformFlow stopped = transformFlow("stopped", FlowState.STOPPED, false, 1);
        TransformFlow invalid = transformFlow("invalid", FlowState.INVALID, false, 2);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setTransformFlows(List.of(
                new TransformFlowSnapshot("running", true, false),
                new TransformFlowSnapshot("stopped", true, true),
                new TransformFlowSnapshot("invalid", true, false),
                new TransformFlowSnapshot("missing", true, true)));

        Mockito.when(transformFlowRepo.findAll()).thenReturn(List.of(running, stopped, invalid));
        Mockito.when(transformFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(transformFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(transformFlowRepo.findById("invalid")).thenReturn(Optional.of(invalid));
        Mockito.when(transformFlowRepo.findById("missing")).thenReturn(Optional.empty());

        Result result = transformFlowService.resetFromSnapshot(systemSnapshot, true);

        // verify the hard reset stopped any running flows
        Mockito.verify(transformFlowRepo).updateFlowState("running", FlowState.STOPPED);

        Mockito.verify(transformFlowRepo).saveAll(flowCaptor.capture());

        Map<String, TransformFlow> updatedFlows = flowCaptor.getValue().stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));

        // running is already running/testMode so no updates are made
        assertThat(updatedFlows).doesNotContainKey("running");

        // stopped flow should be restarted since it was marked as running in the snapshot, it should also be in test mode
        assertThat(updatedFlows.get("stopped").isRunning()).isTrue();
        assertThat(updatedFlows.get("stopped").isTestMode()).isTrue();
        assertThat(updatedFlows.get("stopped").getMaxErrors()).isEqualTo(-1); // reset to the default

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getInfo()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .contains("Flow missing is no longer installed")
                .contains("Flow: invalid is invalid and cannot be started");
    }

    @Test
    void testIngressFlowErrorsExceeded() {
        setupErrorExceeded();
        List<IngressFlowErrorState> errorStates = transformFlowService.ingressFlowErrorsExceeded();
        assertEquals(2, errorStates.size());
        assertEquals(new IngressFlowErrorState("flow1", 1, 0), errorStates.get(0));
        assertEquals(new IngressFlowErrorState("flow3", 6, 5), errorStates.get(1));
    }

    @Test
    void testFlowErrorsExceeded() {
        setupErrorExceeded();
        Set<String> errorsExceeded = transformFlowService.flowErrorsExceeded();
        assertEquals(2, errorsExceeded.size());
        assertThat(errorsExceeded).contains("flow1").contains("flow3");
    }

    void setupErrorExceeded() {
        TransformFlow flow1 = transformFlow("flow1", FlowState.RUNNING, false, 0);
        TransformFlow flow2 = transformFlow("flow2", FlowState.RUNNING, false, 5);
        TransformFlow flow3 = transformFlow("flow3", FlowState.RUNNING, false, 5);
        TransformFlow flow4 = transformFlow("flow4", FlowState.STOPPED, false, 5);

        Mockito.when(transformFlowRepo.findAll()).thenReturn(List.of(flow1, flow2, flow3, flow4));
        Mockito.when(errorCountService.errorsForFlow("flow1")).thenReturn(1);
        Mockito.when(errorCountService.errorsForFlow("flow2")).thenReturn(5);
        Mockito.when(errorCountService.errorsForFlow("flow3")).thenReturn(6);

        transformFlowService.refreshCache();
    }

    TransformFlow transformFlow(String name, FlowState flowState, boolean testMode, int maxErrors) {
        TransformFlow transformFlow = new TransformFlow();
        transformFlow.setName(name);
        transformFlow.setMaxErrors(maxErrors);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        flowStatus.setTestMode(testMode);
        transformFlow.setFlowStatus(flowStatus);
        return transformFlow;
    }
}