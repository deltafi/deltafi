/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.exceptions.MissingFlowException;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.generated.types.RateLimit;
import org.deltafi.core.generated.types.RateLimitInput;
import org.deltafi.core.generated.types.RateLimitUnit;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSinkServiceTest {

    private DataSinkService dataSinkService;

    @Mock
    private DataSinkRepo dataSinkRepo;

    @Mock
    private BuildProperties buildProperties;

    @Captor
    private ArgumentCaptor<Collection<DataSink>> flowCaptor;

    @Captor
    ArgumentCaptor<DataSink> singleFlowCaptor;

    @Mock
    private FlowCacheService flowCacheService;

    @Mock
    private RateLimitService rateLimitService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(buildProperties.getVersion()).thenReturn("1.0.0");
        dataSinkService = spy(new DataSinkService(
                dataSinkRepo,
                null,  // pluginVariableService
                null,  // flowValidator
                buildProperties,
                flowCacheService,
                null,  // eventService
                rateLimitService
        ));
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

        Mockito.verify(dataSinkRepo).saveAll(flowCaptor.capture());

        Map<String, DataSink> updatedFlows = flowCaptor.getValue().stream()
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

        // verify placeholder was upserted for missing flow (with system-plugin since no sourcePlugin in old snapshot)
        Mockito.verify(dataSinkRepo).upsertFlowFromSnapshot(singleFlowCaptor.capture());
        DataSink missingPlaceholder = singleFlowCaptor.getValue();
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

        Mockito.verify(dataSinkRepo, Mockito.times(2)).upsertFlowFromSnapshot(singleFlowCaptor.capture());

        Map<String, DataSink> savedFlows = singleFlowCaptor.getAllValues().stream()
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

    @Test
    void testSetRateLimitSuccess() throws JsonProcessingException {
        String flowName = "test-data-sink";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build();

        DataSink dataSink = dataSink(flowName, FlowState.RUNNING, false, null, true);
        String expectedJson = OBJECT_MAPPER.writeValueAsString(rateLimitInput);

        doReturn(dataSink).when(dataSinkService).getFlowOrThrow(flowName);
        when(dataSinkRepo.updateRateLimit(flowName, expectedJson)).thenReturn(dataSink);

        boolean result = dataSinkService.setRateLimit(flowName, rateLimitInput);

        assertThat(result).isTrue();
        verify(dataSinkRepo).updateRateLimit(flowName, expectedJson);
        verify(rateLimitService).updateLimit("datasink:" + flowName, 100L, Duration.ofSeconds(60));
        verify(flowCacheService).updateCache(dataSink);
    }

    @Test
    void testSetRateLimitAlreadySet() {
        String flowName = "test-data-sink";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build();

        DataSink dataSink = dataSink(flowName, FlowState.RUNNING, false, null, true);
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build());

        doReturn(dataSink).when(dataSinkService).getFlowOrThrow(flowName);

        boolean result = dataSinkService.setRateLimit(flowName, rateLimitInput);

        assertThat(result).isFalse();
        verify(dataSinkRepo, never()).updateRateLimit(any(), any());
        verify(rateLimitService, never()).updateLimit(any(), anyLong(), any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testSetRateLimitDatabaseUpdateFails() throws JsonProcessingException {
        String flowName = "test-data-sink";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1000L)
                .durationSeconds(30)
                .build();

        DataSink dataSink = dataSink(flowName, FlowState.RUNNING, false, null, true);
        String expectedJson = OBJECT_MAPPER.writeValueAsString(rateLimitInput);

        doReturn(dataSink).when(dataSinkService).getFlowOrThrow(flowName);
        when(dataSinkRepo.updateRateLimit(flowName, expectedJson)).thenReturn(null);

        boolean result = dataSinkService.setRateLimit(flowName, rateLimitInput);

        assertThat(result).isFalse();
        verify(dataSinkRepo).updateRateLimit(flowName, expectedJson);
        verify(rateLimitService, never()).updateLimit(any(), anyLong(), any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testSetRateLimitFlowNotFound() {
        String flowName = "non-existent-flow";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(50L)
                .durationSeconds(120)
                .build();

        doThrow(MissingFlowException.notFound("flow not found", FlowType.DATA_SINK))
                .when(dataSinkService).getFlowOrThrow(flowName);

        assertThatThrownBy(() -> dataSinkService.setRateLimit(flowName, rateLimitInput))
                .isInstanceOf(MissingFlowException.class);

        verify(dataSinkRepo, never()).updateRateLimit(any(), any());
        verify(rateLimitService, never()).updateLimit(any(), anyLong(), any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testRemoveRateLimitSuccess() {
        String flowName = "test-data-sink";

        DataSink dataSink = dataSink(flowName, FlowState.RUNNING, false, null, true);
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build());

        doReturn(dataSink).when(dataSinkService).getFlowOrThrow(flowName);
        when(dataSinkRepo.removeRateLimit(flowName)).thenReturn(dataSink);

        boolean result = dataSinkService.removeRateLimit(flowName);

        assertThat(result).isTrue();
        verify(dataSinkRepo).removeRateLimit(flowName);
        verify(rateLimitService).removeBucket("datasink:" + flowName);
        verify(flowCacheService).updateCache(dataSink);
    }

    @Test
    void testRemoveRateLimitNotSet() {
        String flowName = "test-data-sink";

        DataSink dataSink = dataSink(flowName, FlowState.RUNNING, false, null, true);

        doReturn(dataSink).when(dataSinkService).getFlowOrThrow(flowName);

        boolean result = dataSinkService.removeRateLimit(flowName);

        assertThat(result).isFalse();
        verify(dataSinkRepo, never()).removeRateLimit(any());
        verify(rateLimitService, never()).removeBucket(any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testRemoveRateLimitDatabaseUpdateFails() {
        String flowName = "test-data-sink";

        DataSink dataSink = dataSink(flowName, FlowState.RUNNING, false, null, true);
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(500L)
                .durationSeconds(30)
                .build());

        doReturn(dataSink).when(dataSinkService).getFlowOrThrow(flowName);
        when(dataSinkRepo.removeRateLimit(flowName)).thenReturn(null);

        boolean result = dataSinkService.removeRateLimit(flowName);

        assertThat(result).isFalse();
        verify(dataSinkRepo).removeRateLimit(flowName);
        verify(rateLimitService, never()).removeBucket(any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testRemoveRateLimitFlowNotFound() {
        String flowName = "non-existent-flow";

        doThrow(MissingFlowException.notFound("flow not found", FlowType.DATA_SINK))
                .when(dataSinkService).getFlowOrThrow(flowName);

        assertThatThrownBy(() -> dataSinkService.removeRateLimit(flowName))
                .isInstanceOf(MissingFlowException.class);

        verify(dataSinkRepo, never()).removeRateLimit(any());
        verify(rateLimitService, never()).removeBucket(any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testSetRateLimitDifferentUnits() throws JsonProcessingException {
        String flowName = "test-data-sink";
        RateLimitInput fileLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(10L)
                .durationSeconds(60)
                .build();

        DataSink dataSink = dataSink(flowName, FlowState.RUNNING, false, null, true);
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1000L)
                .durationSeconds(60)
                .build());

        String expectedJson = OBJECT_MAPPER.writeValueAsString(fileLimitInput);

        doReturn(dataSink).when(dataSinkService).getFlowOrThrow(flowName);
        when(dataSinkRepo.updateRateLimit(flowName, expectedJson)).thenReturn(dataSink);

        boolean result = dataSinkService.setRateLimit(flowName, fileLimitInput);

        assertThat(result).isTrue();
        verify(dataSinkRepo).updateRateLimit(flowName, expectedJson);
        verify(rateLimitService).updateLimit("datasink:" + flowName, 10L, Duration.ofSeconds(60));
        verify(flowCacheService).updateCache(dataSink);
    }

    @Test
    void testFlowSpecificUpdateFromSnapshotWithRateLimit() {
        String flowName = "test-data-sink";
        DataSink flow = dataSink(flowName, FlowState.RUNNING, false, null, true);
        flow.setRateLimit(null);

        DataSinkSnapshot snapshot = new DataSinkSnapshot(flowName);
        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build();
        snapshot.setRateLimit(rateLimit);

        Result result = new Result();
        boolean changed = dataSinkService.flowSpecificUpdateFromSnapshot(flow, snapshot, result);

        assertThat(changed).isTrue();
        assertThat(flow.getRateLimit()).isEqualTo(rateLimit);
        verify(rateLimitService).updateLimit("datasink:" + flowName, 100L, Duration.ofSeconds(60));
    }

    @Test
    void testFlowSpecificUpdateFromSnapshotRemoveRateLimit() {
        String flowName = "test-data-sink";
        DataSink flow = dataSink(flowName, FlowState.RUNNING, false, null, true);
        RateLimit existingRateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1000L)
                .durationSeconds(30)
                .build();
        flow.setRateLimit(existingRateLimit);

        DataSinkSnapshot snapshot = new DataSinkSnapshot(flowName);
        snapshot.setRateLimit(null);

        Result result = new Result();
        boolean changed = dataSinkService.flowSpecificUpdateFromSnapshot(flow, snapshot, result);

        assertThat(changed).isTrue();
        assertThat(flow.getRateLimit()).isNull();
        verify(rateLimitService).removeBucket("datasink:" + flowName);
    }

    @Test
    void testFlowSpecificUpdateFromSnapshotNoChanges() {
        String flowName = "test-data-sink";
        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(50L)
                .durationSeconds(120)
                .build();

        DataSink flow = dataSink(flowName, FlowState.RUNNING, false, null, true);
        flow.setRateLimit(rateLimit);

        DataSinkSnapshot snapshot = new DataSinkSnapshot(flowName);
        snapshot.setRateLimit(rateLimit);

        Result result = new Result();
        boolean changed = dataSinkService.flowSpecificUpdateFromSnapshot(flow, snapshot, result);

        assertThat(changed).isFalse();
        assertThat(flow.getRateLimit()).isEqualTo(rateLimit);
        verify(rateLimitService, never()).updateLimit(any(), anyLong(), any());
        verify(rateLimitService, never()).removeBucket(any());
    }

    @Test
    void testOnRefreshCacheDoesNotResetBuckets() {
        DataSink flowWithRateLimit1 = dataSink("flow1", FlowState.RUNNING, false, null, true);
        flowWithRateLimit1.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build());

        DataSink flowWithRateLimit2 = dataSink("flow2", FlowState.RUNNING, false, null, true);
        flowWithRateLimit2.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(500L)
                .durationSeconds(30)
                .build());

        DataSink flowWithoutRateLimit = dataSink("flow3", FlowState.RUNNING, false, null, true);

        List<Flow> allFlows = List.of(flowWithRateLimit1, flowWithRateLimit2, flowWithoutRateLimit);
        when(flowCacheService.flowsOfType(FlowType.DATA_SINK)).thenReturn(allFlows);

        dataSinkService.onRefreshCache();

        verify(rateLimitService, never()).updateLimit(anyString(), anyLong(), any());
    }

    @Test
    void testPostConstructInitializesBuckets() {
        DataSink flowWithRateLimit1 = dataSink("flow1", FlowState.RUNNING, false, null, true);
        flowWithRateLimit1.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build());

        DataSink flowWithRateLimit2 = dataSink("flow2", FlowState.RUNNING, false, null, true);
        flowWithRateLimit2.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(500L)
                .durationSeconds(30)
                .build());

        DataSink flowWithoutRateLimit = dataSink("flow3", FlowState.RUNNING, false, null, true);

        List<Flow> allFlows = List.of(flowWithRateLimit1, flowWithRateLimit2, flowWithoutRateLimit);
        when(flowCacheService.flowsOfType(FlowType.DATA_SINK)).thenReturn(allFlows);

        dataSinkService.postConstruct();

        verify(dataSinkRepo).updateSystemPluginFlowVersions("1.0.0", FlowType.DATA_SINK);
        verify(rateLimitService).updateLimit("datasink:flow1", 100L, Duration.ofSeconds(60));
        verify(rateLimitService).updateLimit("datasink:flow2", 500L, Duration.ofSeconds(30));
        verify(rateLimitService, never()).updateLimit(eq("datasink:flow3"), anyLong(), any());
    }

    @Test
    void testDataSinkBucketName() {
        assertThat(DataSinkService.dataSinkBucketName("my-data-sink")).isEqualTo("datasink:my-data-sink");
        assertThat(DataSinkService.dataSinkBucketName("another-sink")).isEqualTo("datasink:another-sink");
    }

    @Test
    void testResetFromSnapshotWithRateLimit() {
        PluginCoordinates pluginCoordinates = PluginCoordinates.builder()
                .groupId("org.deltafi")
                .artifactId("test-plugin")
                .version("1.0.0")
                .build();

        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build();

        DataSinkSnapshot snapshotWithRateLimit = new DataSinkSnapshot("pending-with-rate-limit", true, false);
        snapshotWithRateLimit.setSourcePlugin(pluginCoordinates);
        snapshotWithRateLimit.setRateLimit(rateLimit);

        Snapshot snapshot = new Snapshot();
        snapshot.setDataSinks(List.of(snapshotWithRateLimit));

        when(flowCacheService.flowsOfType(FlowType.DATA_SINK)).thenReturn(List.of());

        Result result = dataSinkService.resetFromSnapshot(snapshot, true);

        verify(dataSinkRepo).upsertFlowFromSnapshot(singleFlowCaptor.capture());

        DataSink placeholder = singleFlowCaptor.getValue();
        assertThat(placeholder.getName()).isEqualTo("pending-with-rate-limit");
        assertThat(placeholder.getRateLimit()).isEqualTo(rateLimit);

        assertThat(result.isSuccess()).isTrue();
    }
}
