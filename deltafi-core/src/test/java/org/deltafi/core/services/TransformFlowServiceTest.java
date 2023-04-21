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

import org.deltafi.common.types.EgressActionConfiguration;
import org.deltafi.common.types.TransformFlowPlan;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.repo.TransformFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.TransformFlow;
import org.deltafi.core.types.Result;
import org.deltafi.core.validation.TransformFlowValidator;
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
class TransformFlowServiceTest {

    private static final List<String> RUNNING_FLOWS = List.of("a", "b");
    private static final List<String> TEST_FLOWS = List.of("a", "b");

    @Mock
    TransformFlowRepo transformFlowRepo;

    @Mock
    TransformFlowValidator flowValidator;

    @InjectMocks
    TransformFlowService transformFlowService;

    @Test
    void buildFlow() {
        TransformFlow running = transformFlow("running", FlowState.RUNNING, true);
        TransformFlow stopped = transformFlow("stopped", FlowState.STOPPED, false);
        Mockito.when(transformFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(transformFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(flowValidator.validate(Mockito.any())).thenReturn(Collections.emptyList());

        TransformFlowPlan runningFlowPlan = new TransformFlowPlan("running", "yep");
        runningFlowPlan.setEgressAction(new EgressActionConfiguration("EgressActionConfig", "EgressActionConfigType"));
        TransformFlowPlan stoppedFlowPlan = new TransformFlowPlan("stopped", "naw");
        stoppedFlowPlan.setEgressAction(new EgressActionConfiguration("EgressActionConfig", "EgressActionConfigType"));

        TransformFlow runningTransformFlow = transformFlowService.buildFlow(runningFlowPlan, Collections.emptyList());
        TransformFlow stoppedTransformFlow = transformFlowService.buildFlow(stoppedFlowPlan, Collections.emptyList());

        assertThat(runningTransformFlow.isRunning()).isTrue();
        assertThat(runningTransformFlow.isTestMode()).isTrue();
        assertThat(stoppedTransformFlow.isRunning()).isFalse();
        assertThat(stoppedTransformFlow.isTestMode()).isFalse();
    }

    @Test
    void updateSnapshot() {
        List<TransformFlow> flows = RUNNING_FLOWS.stream().map(this::runningFlow).collect(Collectors.toList());

        flows.add(transformFlow("c", FlowState.STOPPED, false));
        Mockito.when(transformFlowRepo.findAll()).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        transformFlowService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getRunningTransformFlows()).isEqualTo(RUNNING_FLOWS);
        assertThat(systemSnapshot.getTestTransformFlows()).isEqualTo(TEST_FLOWS);
    }

    @Test
    void testResetFromSnapshot() {
        TransformFlow running = transformFlow("running", FlowState.RUNNING, true);
        TransformFlow stopped = transformFlow("stopped", FlowState.STOPPED, false);
        TransformFlow invalid = transformFlow("invalid", FlowState.INVALID, false);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setRunningTransformFlows(List.of("running", "stopped", "invalid", "missing"));
        systemSnapshot.setTestTransformFlows(List.of("stopped","missing"));

        Mockito.when(transformFlowRepo.findAll()).thenReturn(List.of(running, stopped, invalid));
        Mockito.when(transformFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(transformFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(transformFlowRepo.findById("invalid")).thenReturn(Optional.of(invalid));
        Mockito.when(transformFlowRepo.findById("missing")).thenReturn(Optional.empty());

        Result result = transformFlowService.resetFromSnapshot(systemSnapshot, true);

        // verify the hard reset stopped any running flows
        Mockito.verify(transformFlowRepo).updateFlowState("running", FlowState.STOPPED);
        // stopped flow should be restarted since it was marked as running in the snapshot
        Mockito.verify(transformFlowRepo).updateFlowState("stopped", FlowState.RUNNING);

        Mockito.verify(transformFlowRepo).updateFlowTestMode("stopped", true);


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
        systemSnapshot.setRunningTransformFlows(List.of("a", "b"));
        systemSnapshot.setTestTransformFlows(List.of("c", "d"));
        assertThat(transformFlowService.getRunningFromSnapshot(systemSnapshot)).isEqualTo(RUNNING_FLOWS);
        assertThat(transformFlowService.getTestModeFromSnapshot(systemSnapshot)).isEqualTo(List.of("c", "d"));
    }

    TransformFlow runningFlow(String name) {
        return transformFlow(name, FlowState.RUNNING, true);
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