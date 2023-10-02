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

import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.repo.TimedIngressFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.FlowSnapshot;
import org.deltafi.core.snapshot.types.TimedIngressFlowSnapshot;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.TimedIngressFlow;
import org.deltafi.core.types.Result;
import org.deltafi.core.validation.TimedIngressFlowValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TimedIngressFlowServiceTest {

    @Mock
    TimedIngressFlowRepo timedIngressFlowRepo;

    @Mock
    TimedIngressFlowValidator flowValidator;

    @Mock
    @SuppressWarnings("unused")
    BuildProperties buildProperties;

    @InjectMocks
    TimedIngressFlowService timedIngressFlowService;

    @Captor
    ArgumentCaptor<List<TimedIngressFlow>> flowCaptor;

    @Test
    void buildFlow() {
        TimedIngressFlow running = timedIngressFlow("running", FlowState.RUNNING, true, "target", Duration.ofMinutes(10));
        TimedIngressFlow stopped = timedIngressFlow("stopped", FlowState.STOPPED, false, "another", Duration.ofMillis(1));
        Mockito.when(timedIngressFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(timedIngressFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(flowValidator.validate(Mockito.any())).thenReturn(Collections.emptyList());

        TimedIngressFlowPlan runningFlowPlan = new TimedIngressFlowPlan("running", "yep");
        runningFlowPlan.setTimedIngressAction(new TimedIngressActionConfiguration("TimedIngressActionConfig", "TimedIngressActionConfigType"));
        TimedIngressFlowPlan stoppedFlowPlan = new TimedIngressFlowPlan("stopped", "naw");
        stoppedFlowPlan.setTimedIngressAction(new TimedIngressActionConfiguration("TimedIngressActionConfig", "TimedIngressActionConfigType"));

        TimedIngressFlow runningTimedIngressFlow = timedIngressFlowService.buildFlow(runningFlowPlan, Collections.emptyList());
        TimedIngressFlow stoppedTimedIngressFlow = timedIngressFlowService.buildFlow(stoppedFlowPlan, Collections.emptyList());

        assertThat(runningTimedIngressFlow.isRunning()).isTrue();
        assertThat(runningTimedIngressFlow.isTestMode()).isTrue();
        assertThat(runningTimedIngressFlow.getInterval()).isEqualTo(Duration.ofMinutes(10));
        assertThat(stoppedTimedIngressFlow.isRunning()).isFalse();
        assertThat(stoppedTimedIngressFlow.isTestMode()).isFalse();
    }

    @Test
    void updateSnapshot() {
        List<TimedIngressFlow> flows = new ArrayList<>();
        flows.add(timedIngressFlow("a", FlowState.RUNNING, false, "targetA", Duration.ofMinutes(1)));
        flows.add(timedIngressFlow("b", FlowState.STOPPED, false, "targetB", Duration.ofMinutes(2)));
        flows.add(timedIngressFlow("c", FlowState.INVALID, true, "targetC", Duration.ofMinutes(3)));
        Mockito.when(timedIngressFlowRepo.findAll()).thenReturn(flows);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        timedIngressFlowService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getTimedIngressFlows()).hasSize(3);

        Map<String, TimedIngressFlowSnapshot> timedIngressFlowSnapshotMap = systemSnapshot.getTimedIngressFlows().stream()
                .collect(Collectors.toMap(FlowSnapshot::getName, Function.identity()));

        TimedIngressFlowSnapshot aFlowSnapshot = timedIngressFlowSnapshotMap.get("a");
        assertThat(aFlowSnapshot.isRunning()).isTrue();
        assertThat(aFlowSnapshot.isTestMode()).isFalse();
        assertThat(aFlowSnapshot.getTargetFlow()).isEqualTo("targetA");
        assertThat(aFlowSnapshot.getInterval()).isEqualTo(Duration.ofMinutes(1));

        TimedIngressFlowSnapshot bFlowSnapshot = timedIngressFlowSnapshotMap.get("b");
        assertThat(bFlowSnapshot.isRunning()).isFalse();
        assertThat(bFlowSnapshot.isTestMode()).isFalse();
        assertThat(bFlowSnapshot.getTargetFlow()).isEqualTo("targetB");
        assertThat(bFlowSnapshot.getInterval()).isEqualTo(Duration.ofMinutes(2));

        TimedIngressFlowSnapshot cFlowSnapshot = timedIngressFlowSnapshotMap.get("c");
        assertThat(cFlowSnapshot.isRunning()).isFalse();
        assertThat(cFlowSnapshot.isTestMode()).isTrue();
        assertThat(cFlowSnapshot.getTargetFlow()).isEqualTo("targetC");
        assertThat(cFlowSnapshot.getInterval()).isEqualTo(Duration.ofMinutes(3));

        assertThat(systemSnapshot.getRunningIngressFlows()).isNull();
        assertThat(systemSnapshot.getTestIngressFlows()).isNull();
    }

    @Test
    void testResetFromSnapshot() {
        TimedIngressFlow running = timedIngressFlow("running", FlowState.RUNNING, true, "targetA", Duration.ofMinutes(1));
        TimedIngressFlow stopped = timedIngressFlow("stopped", FlowState.STOPPED, false, "targetB", Duration.ofMinutes(2));
        TimedIngressFlow invalid = timedIngressFlow("invalid", FlowState.INVALID, false, "targetC", Duration.ofMinutes(3));
        TimedIngressFlow changed = timedIngressFlow("changed", FlowState.STOPPED, false, "changeMe", Duration.ofMinutes(9999));

        SystemSnapshot systemSnapshot = new SystemSnapshot();

        // create snapshot objects
        List<TimedIngressFlowSnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot("running", true, false, "targetA", Duration.ofMinutes(1)));
        snapshots.add(snapshot("stopped", true, true, "targetB", Duration.ofMinutes(2)));
        snapshots.add(snapshot("invalid", true, false, "targetC", Duration.ofMinutes(3)));
        snapshots.add(snapshot("changed", false, true, "targetD", Duration.ofMinutes(4)));
        snapshots.add(snapshot("missing", false, true, "missing", Duration.ofSeconds(1)));
        systemSnapshot.setTimedIngressFlows(snapshots);

        Mockito.when(timedIngressFlowRepo.findAll()).thenReturn(List.of(running, stopped, invalid, changed));
        Mockito.when(timedIngressFlowRepo.findById("running")).thenReturn(Optional.of(running));
        Mockito.when(timedIngressFlowRepo.findById("stopped")).thenReturn(Optional.of(stopped));
        Mockito.when(timedIngressFlowRepo.findById("invalid")).thenReturn(Optional.of(invalid));
        Mockito.when(timedIngressFlowRepo.findById("missing")).thenReturn(Optional.empty());
        Mockito.when(timedIngressFlowRepo.findById("changed")).thenReturn(Optional.of(changed));

        Result result = timedIngressFlowService.resetFromSnapshot(systemSnapshot, true);

        // verify the hard reset stopped any running flows
        Mockito.verify(timedIngressFlowRepo).updateFlowState("running", FlowState.STOPPED);
        Mockito.verify(timedIngressFlowRepo).saveAll(flowCaptor.capture());

        Map<String, TimedIngressFlow> updatedFlows = flowCaptor.getValue().stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));

        assertThat(updatedFlows.get("running").isRunning()).isTrue();
        assertThat(updatedFlows.get("running").getTargetFlow()).isEqualTo("targetA");
        assertThat(updatedFlows.get("running").getInterval()).isEqualTo(Duration.ofMinutes(1));
        // stopped flow should be restarted since it was marked as running in the snapshot, it should also be in test mode
        assertThat(updatedFlows.get("stopped").isRunning()).isTrue();
        assertThat(updatedFlows.get("stopped").isTestMode()).isTrue();
        assertThat(updatedFlows.get("stopped").getTargetFlow()).isEqualTo("targetB");
        assertThat(updatedFlows.get("stopped").getInterval()).isEqualTo(Duration.ofMinutes(2));

        assertThat(updatedFlows.get("changed").getTargetFlow()).isEqualTo("targetD");
        assertThat(updatedFlows.get("changed").getInterval()).isEqualTo(Duration.ofMinutes(4));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getInfo()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .contains("Flow missing is no longer installed")
                .contains("Flow: invalid is invalid and cannot be started");
    }

    TimedIngressFlow timedIngressFlow(String name, FlowState flowState, boolean testMode, String targetFlow, Duration interval) {
        TimedIngressFlow timedIngressFlow = new TimedIngressFlow();
        timedIngressFlow.setName(name);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        flowStatus.setTestMode(testMode);
        timedIngressFlow.setFlowStatus(flowStatus);
        timedIngressFlow.setSchemaVersion(TimedIngressFlow.CURRENT_SCHEMA_VERSION);
        timedIngressFlow.setTargetFlow(targetFlow);
        timedIngressFlow.setInterval(interval);
        return timedIngressFlow;
    }

    TimedIngressFlowSnapshot snapshot(String name, boolean running, boolean testMode, String targetFlow, Duration interval) {
        TimedIngressFlowSnapshot snapshot = new TimedIngressFlowSnapshot(name);
        snapshot.setRunning(running);
        snapshot.setTestMode(testMode);
        snapshot.setTargetFlow(targetFlow);
        snapshot.setInterval(interval);
        return snapshot;
    }
}