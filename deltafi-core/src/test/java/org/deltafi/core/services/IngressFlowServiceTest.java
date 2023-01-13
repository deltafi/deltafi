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

import org.deltafi.common.types.IngressFlowPlan;
import org.deltafi.common.types.LoadActionConfiguration;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.repo.IngressFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.IngressFlow;
import org.deltafi.core.types.Result;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class IngressFlowServiceTest {

    private static final List<String> RUNNING_FLOWS = List.of("a", "b");
    private static final List<String> TEST_FLOWS = List.of("a", "b");

    @Mock
    IngressFlowRepo ingressFlowRepo;

    @Mock
    IngressFlowValidator flowValidator;

    @InjectMocks
    IngressFlowService ingressFlowService;

    @Test
    void buildFlow() {
        IngressFlow running = ingressFlow("running", FlowState.RUNNING, true);
        IngressFlow stopped = ingressFlow("stopped", FlowState.STOPPED, false);
        Mockito.when(ingressFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(ingressFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(flowValidator.validate(Mockito.any())).thenReturn(Collections.emptyList());

        IngressFlowPlan runningFlowPlan = new IngressFlowPlan("running", "yep", new LoadActionConfiguration("LoadActionConfig", "LoadActionConfigType"));
        IngressFlowPlan stoppedFlowPlan = new IngressFlowPlan("stopped", "naw", new LoadActionConfiguration("LoadActionConfig", "LoadActionConfigType"));

        IngressFlow runningIngressFlow = ingressFlowService.buildFlow(runningFlowPlan, Collections.emptyList());
        IngressFlow stoppedIngressFlow = ingressFlowService.buildFlow(stoppedFlowPlan, Collections.emptyList());

        assertThat(runningIngressFlow.isRunning()).isTrue();
        assertThat(runningIngressFlow.isTestMode()).isTrue();
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
        systemSnapshot.setTestIngressFlows(List.of("stopped","missing"));

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
        return ingressFlow;
    }
}