/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.repo.DataSinkRepo;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.snapshot.DataSinkSnapshot;
import org.deltafi.core.types.snapshot.FlowSnapshot;
import org.deltafi.core.types.DataSink;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.info.BuildProperties;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DataSinkServiceTest {

    @InjectMocks
    DataSinkService dataSinkService;

    @Mock
    DataSinkRepo dataSinkRepo;

    @Mock
    BuildProperties buildProperties;

    @Captor
    ArgumentCaptor<Collection<DataSink>> flowCaptor;

    @Mock
    FlowCacheService flowCacheService;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(buildProperties.getVersion()).thenReturn("1.0.0");
    }

    @Test
    void updateSnapshot() {
        List<Flow> flows = new ArrayList<>();
        flows.add(dataSink("a", FlowState.RUNNING, false, null));
        flows.add(dataSink("b", FlowState.STOPPED, false, Set.of("a", "b")));
        flows.add(dataSink("c", FlowState.STOPPED, true, Set.of()));

        Mockito.when(flowCacheService.flowsOfType(FlowType.DATA_SINK)).thenReturn(flows);

        Snapshot snapshot = new Snapshot();
        dataSinkService.updateSnapshot(snapshot);

        assertThat(snapshot.getDataSinks()).hasSize(3);

        Map<String, DataSinkSnapshot> dataSinkSnapshotMap = snapshot.getDataSinks().stream()
                .collect(Collectors.toMap(FlowSnapshot::getName, Function.identity()));

        DataSinkSnapshot aFlowSnapshot = dataSinkSnapshotMap.get("a");
        assertThat(aFlowSnapshot.isRunning()).isTrue();
        assertThat(aFlowSnapshot.isTestMode()).isFalse();
        assertThat(aFlowSnapshot.getExpectedAnnotations()).isNull();

        DataSinkSnapshot bFlowSnapshot = dataSinkSnapshotMap.get("b");
        assertThat(bFlowSnapshot.isRunning()).isFalse();
        assertThat(bFlowSnapshot.isTestMode()).isFalse();
        assertThat(bFlowSnapshot.getExpectedAnnotations()).hasSize(2).contains("a", "b");

        DataSinkSnapshot cFlowSnapshot = dataSinkSnapshotMap.get("c");
        assertThat(cFlowSnapshot.isRunning()).isFalse();
        assertThat(cFlowSnapshot.isTestMode()).isTrue();
        assertThat(cFlowSnapshot.getExpectedAnnotations()).isEmpty();
    }

    @Test
    void testResetFromSnapshot() {
        DataSink running = dataSink("running", FlowState.RUNNING, true, Set.of());
        DataSink stopped = dataSink("stopped", FlowState.STOPPED, false, Set.of());
        DataSink invalid = dataSink("invalid", FlowState.RUNNING, false, Set.of(), false);

        Snapshot snapshot = new Snapshot();
        snapshot.setDataSinks(List.of(
                new DataSinkSnapshot("running", true, false),
                new DataSinkSnapshot("stopped", true, true),
                new DataSinkSnapshot("invalid", true, false),
                new DataSinkSnapshot("missing", true, true)));

        Mockito.when(flowCacheService.flowsOfType(FlowType.DATA_SINK)).thenReturn(List.of(running, stopped, invalid));

        Result result = dataSinkService.resetFromSnapshot(snapshot, true);

        Mockito.verify(dataSinkRepo, Mockito.times(2)).saveAll(flowCaptor.capture());

        // First call is for updated flows, second is for placeholders
        List<Collection<DataSink>> allSaves = flowCaptor.getAllValues();
        Map<String, DataSink> updatedFlows = allSaves.get(0).stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));

        assertThat(updatedFlows).hasSize(3);

        // running is set back to running after state was reset
        assertThat(updatedFlows.get("running").isRunning()).isTrue();
        assertThat(updatedFlows.get("running").isTestMode()).isFalse();

        // stopped dataSource should be restarted since it was marked as running in the snapshot, it should also be in test mode
        assertThat(updatedFlows.get("stopped").isRunning()).isTrue();
        assertThat(updatedFlows.get("stopped").isTestMode()).isTrue();

        // flow named invalid is set back to a running state after the state was reset in hard reset
        assertThat(updatedFlows.get("invalid").isRunning()).isTrue();
        assertThat(updatedFlows.get("invalid").isInvalid()).isTrue();
        assertThat(updatedFlows.get("invalid").isTestMode()).isFalse();

        // verify placeholder was created for missing flow (with system-plugin since no sourcePlugin in old snapshot)
        Collection<DataSink> placeholders = allSaves.get(1);
        assertThat(placeholders).hasSize(1);
        DataSink missingPlaceholder = placeholders.iterator().next();
        assertThat(missingPlaceholder.getName()).isEqualTo("missing");
        assertThat(missingPlaceholder.getFlowStatus().getPlaceholder()).isTrue();
        assertThat(missingPlaceholder.isRunning()).isTrue();
        assertThat(missingPlaceholder.isTestMode()).isTrue();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInfo()).hasSize(1)
                .anyMatch(info -> info.contains("Created placeholder for flow missing"));
    }

    @Test
    void testResetFromSnapshot_createsPlaceholderForMissingFlowWithSourcePlugin() {
        PluginCoordinates pluginCoordinates = PluginCoordinates.builder()
                .groupId("org.deltafi")
                .artifactId("test-plugin")
                .version("1.0.0")
                .build();

        DataSinkSnapshot runningSnapshot = new DataSinkSnapshot("pending-running", true, false);
        runningSnapshot.setSourcePlugin(pluginCoordinates);
        runningSnapshot.setExpectedAnnotations(Set.of("annotation1"));

        DataSinkSnapshot stoppedSnapshot = new DataSinkSnapshot("pending-stopped", false, true);
        stoppedSnapshot.setSourcePlugin(pluginCoordinates);

        Snapshot snapshot = new Snapshot();
        snapshot.setDataSinks(List.of(runningSnapshot, stoppedSnapshot));

        Mockito.when(flowCacheService.flowsOfType(FlowType.DATA_SINK)).thenReturn(List.of());

        Result result = dataSinkService.resetFromSnapshot(snapshot, true);

        Mockito.verify(dataSinkRepo).saveAll(flowCaptor.capture());

        Map<String, DataSink> savedFlows = flowCaptor.getValue().stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));

        assertThat(savedFlows).hasSize(2);

        // Verify running placeholder
        DataSink runningPlaceholder = savedFlows.get("pending-running");
        assertThat(runningPlaceholder).isNotNull();
        assertThat(runningPlaceholder.isRunning()).isTrue();
        assertThat(runningPlaceholder.isTestMode()).isFalse();
        assertThat(runningPlaceholder.isInvalid()).isTrue();
        assertThat(runningPlaceholder.getSourcePlugin()).isEqualTo(pluginCoordinates);
        assertThat(runningPlaceholder.getExpectedAnnotations()).containsExactly("annotation1");
        assertThat(runningPlaceholder.getFlowStatus().getErrors()).hasSize(1);
        assertThat(runningPlaceholder.getFlowStatus().getErrors().get(0).getErrorType()).isEqualTo(FlowErrorType.INVALID_CONFIG);
        assertThat(runningPlaceholder.getFlowStatus().getErrors().get(0).getMessage()).contains("Waiting for plugin");

        // Verify stopped placeholder with testMode
        DataSink stoppedPlaceholder = savedFlows.get("pending-stopped");
        assertThat(stoppedPlaceholder).isNotNull();
        assertThat(stoppedPlaceholder.isRunning()).isFalse();
        assertThat(stoppedPlaceholder.isTestMode()).isTrue();
        assertThat(stoppedPlaceholder.isInvalid()).isTrue();
        assertThat(stoppedPlaceholder.getSourcePlugin()).isEqualTo(pluginCoordinates);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInfo()).hasSize(2)
                .anyMatch(info -> info.contains("Created placeholder for flow pending-running"))
                .anyMatch(info -> info.contains("Created placeholder for flow pending-stopped"));
    }

    DataSink dataSink(String name, FlowState flowState, boolean testMode, Set<String> expectedAnnotations) {
        return dataSink(name, flowState, testMode, expectedAnnotations, true);
    }

    DataSink dataSink(String name, FlowState flowState, boolean testMode, Set<String> expectedAnnotations, boolean valid) {
        DataSink dataSink = new DataSink();
        dataSink.setName(name);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        flowStatus.setTestMode(testMode);
        flowStatus.setValid(valid);
        dataSink.setFlowStatus(flowStatus);
        dataSink.setExpectedAnnotations(expectedAnnotations);
        return dataSink;
    }
}