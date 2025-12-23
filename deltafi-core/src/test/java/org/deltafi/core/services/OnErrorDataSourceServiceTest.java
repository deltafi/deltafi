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

import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.DataSourceErrorState;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.repo.OnErrorDataSourceRepo;
import org.deltafi.core.types.*;
import org.deltafi.core.types.snapshot.FlowSnapshot;
import org.deltafi.core.types.snapshot.OnErrorDataSourceSnapshot;
import org.deltafi.core.types.snapshot.Snapshot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class OnErrorDataSourceServiceTest {

    @Mock
    OnErrorDataSourceRepo onErrorDataSourceRepo;

    @Mock
    BuildProperties buildProperties;

    @InjectMocks
    OnErrorDataSourceService onErrorDataSourceService;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(buildProperties.getVersion()).thenReturn("1.0.0");
    }

    @Mock
    ErrorCountService errorCountService;

    @Captor
    ArgumentCaptor<Collection<OnErrorDataSource>> flowCaptor;

    @Mock
    FlowCacheService flowCacheService;

    @Test
    void testGetTriggeredDataSources() {
        // Create data source with mixed filters - should match transform "transformB" with action "actionA"
        OnErrorDataSource dataSource1 = onErrorDataSource("errorFlow1", FlowState.RUNNING, false, ".*error.*", 
                List.of(
                    new ErrorSourceFilter(null, null, "actionA", null),
                    new ErrorSourceFilter(FlowType.TRANSFORM, "transformB", null, null)
                ),
                List.of(new KeyValue("key1", "value1")), List.of(new KeyValue("annotation1", "annotationValue1")),
                List.of("metadata.*"), List.of("annotation.*"));

        // Create catch-all data source
        OnErrorDataSource dataSource2 = onErrorDataSource("errorFlow2", FlowState.RUNNING, false, ".*exception.*", 
                null, null, null, null, null);

        // Create stopped data source (should not trigger)
        OnErrorDataSource dataSource3 = onErrorDataSource("errorFlow3", FlowState.STOPPED, false, ".*error.*", 
                null, null, null, null, null);

        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(
                List.of(dataSource1, dataSource2, dataSource3));

        // Test matching criteria for dataSource1
        Map<String, String> metadata = Map.of("key1", "value1", "otherKey", "otherValue");
        Map<String, String> annotations = Map.of("annotation1", "annotationValue1");

        List<OnErrorDataSource> triggered = onErrorDataSourceService.getTriggeredDataSources(
                "transformB", FlowType.TRANSFORM, "actionA", "com.example.ActionClass", "This is an error message", metadata, annotations);

        assertThat(triggered).hasSize(1);
        assertThat(triggered.getFirst().getName()).isEqualTo("errorFlow1");

        // Test matching criteria for dataSource2 (no specific filters)
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "anyFlow", FlowType.TRANSFORM, "anyAction", "com.example.AnyClass", "This is an exception message", Map.of(), Map.of());

        assertThat(triggered).hasSize(1);
        assertThat(triggered.getFirst().getName()).isEqualTo("errorFlow2");

        // Test no matches due to stopped state
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "anyFlow", FlowType.TRANSFORM, "anyAction", "com.example.AnyClass", "This is an error message", Map.of(), Map.of());

        assertThat(triggered).isEmpty();
    }

    @Test
    void testErrorMessageRegexFiltering() {
        OnErrorDataSource regexDataSource = onErrorDataSource("regex", FlowState.RUNNING, false, ".*critical.*", 
                null, null, null, null, null);
        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of(regexDataSource));

        List<OnErrorDataSource> triggered = onErrorDataSourceService.getTriggeredDataSources(
                "flow", FlowType.TRANSFORM, "action", "com.example.ActionClass", "This is a critical error", Map.of(), Map.of());
        assertThat(triggered).hasSize(1);

        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "flow", FlowType.TRANSFORM, "action", "com.example.ActionClass", "This is a minor error", Map.of(), Map.of());
        assertThat(triggered).isEmpty();
    }

    @Test
    void testSourceFilterActionNameMatching() {
        OnErrorDataSource actionDataSource = onErrorDataSource("action", FlowState.RUNNING, false, null, 
                List.of(new ErrorSourceFilter(null, null, "specificAction", null)), null, null, null, null);
        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of(actionDataSource));

        List<OnErrorDataSource> triggered = onErrorDataSourceService.getTriggeredDataSources(
                "flow", FlowType.TRANSFORM, "specificAction", "com.example.ActionClass", "error", Map.of(), Map.of());
        assertThat(triggered).hasSize(1);

        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "flow", FlowType.TRANSFORM, "otherAction", "com.example.ActionClass", "error", Map.of(), Map.of());
        assertThat(triggered).isEmpty();
    }

    @Test
    void testSourceFilterFlowTypeAndNameMatching() {
        OnErrorDataSource transformDataSource = onErrorDataSource("transform", FlowState.RUNNING, false, null, 
                List.of(new ErrorSourceFilter(FlowType.TRANSFORM, "specificTransform", null, null)), null, null, null, null);
        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of(transformDataSource));

        List<OnErrorDataSource> triggered = onErrorDataSourceService.getTriggeredDataSources(
                "specificTransform", FlowType.TRANSFORM, "action", "com.example.ActionClass", "error", Map.of(), Map.of());
        assertThat(triggered).hasSize(1);

        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "otherTransform", FlowType.TRANSFORM, "action", "com.example.ActionClass", "error", Map.of(), Map.of());
        assertThat(triggered).isEmpty();

        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "specificTransform", FlowType.DATA_SINK, "action", "com.example.ActionClass", "error", Map.of(), Map.of());
        assertThat(triggered).isEmpty();
    }

    @Test
    void testSourceFilterActionClassMatching() {
        OnErrorDataSource classDataSource = onErrorDataSource("class", FlowState.RUNNING, false, null, 
                List.of(new ErrorSourceFilter(null, null, null, "com.example.ValidationAction")), null, null, null, null);
        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of(classDataSource));

        List<OnErrorDataSource> triggered = onErrorDataSourceService.getTriggeredDataSources(
                "anyFlow", FlowType.TRANSFORM, "anyAction", "com.example.ValidationAction", "error", Map.of(), Map.of());
        assertThat(triggered).hasSize(1);

        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "anyFlow", FlowType.TRANSFORM, "anyAction", "com.example.OtherAction", "error", Map.of(), Map.of());
        assertThat(triggered).isEmpty();
    }

    @Test
    void testSourceFilterOrLogic() {
        // Data source with multiple filters - should match if ANY filter matches (OR logic)
        OnErrorDataSource multiFilterDataSource = onErrorDataSource("multiFilter", FlowState.RUNNING, false, null, 
                List.of(
                    new ErrorSourceFilter(null, null, "actionA", null),
                    new ErrorSourceFilter(FlowType.TRANSFORM, "transformB", null, null),
                    new ErrorSourceFilter(null, null, null, "com.example.ValidationAction")
                ), null, null, null, null);
        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of(multiFilterDataSource));

        // Should match first filter (actionA)
        List<OnErrorDataSource> triggered = onErrorDataSourceService.getTriggeredDataSources(
                "someFlow", FlowType.DATA_SINK, "actionA", "com.other.ActionClass", "error", Map.of(), Map.of());
        assertThat(triggered).hasSize(1);

        // Should match second filter (transformB)
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "transformB", FlowType.TRANSFORM, "someAction", "com.other.ActionClass", "error", Map.of(), Map.of());
        assertThat(triggered).hasSize(1);

        // Should match third filter (ValidationAction class)
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "someFlow", FlowType.DATA_SINK, "someAction", "com.example.ValidationAction", "error", Map.of(), Map.of());
        assertThat(triggered).hasSize(1);

        // Should not match any filter
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "otherFlow", FlowType.DATA_SINK, "otherAction", "com.other.ActionClass", "error", Map.of(), Map.of());
        assertThat(triggered).isEmpty();
    }

    @Test
    void testSourceFilterAndLogic() {
        // Data source with filter that requires ALL fields to match (AND logic within filter)
        OnErrorDataSource andFilterDataSource = onErrorDataSource("andFilter", FlowState.RUNNING, false, null, 
                List.of(new ErrorSourceFilter(FlowType.TRANSFORM, "specificTransform", "specificAction", "com.example.SpecificClass")), 
                null, null, null, null);
        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of(andFilterDataSource));

        // Should match - all fields match
        List<OnErrorDataSource> triggered = onErrorDataSourceService.getTriggeredDataSources(
                "specificTransform", FlowType.TRANSFORM, "specificAction", "com.example.SpecificClass", "error", Map.of(), Map.of());
        assertThat(triggered).hasSize(1);

        // Should not match - wrong flow type
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "specificTransform", FlowType.DATA_SINK, "specificAction", "com.example.SpecificClass", "error", Map.of(), Map.of());
        assertThat(triggered).isEmpty();

        // Should not match - wrong flow name
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "otherTransform", FlowType.TRANSFORM, "specificAction", "com.example.SpecificClass", "error", Map.of(), Map.of());
        assertThat(triggered).isEmpty();

        // Should not match - wrong action name
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "specificTransform", FlowType.TRANSFORM, "otherAction", "com.example.SpecificClass", "error", Map.of(), Map.of());
        assertThat(triggered).isEmpty();

        // Should not match - wrong action class
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "specificTransform", FlowType.TRANSFORM, "specificAction", "com.example.OtherClass", "error", Map.of(), Map.of());
        assertThat(triggered).isEmpty();
    }

    @Test
    void testComplexAndOrLogic() {
        // Data source with multiple filters, each with different AND requirements
        OnErrorDataSource complexDataSource = onErrorDataSource("complex", FlowState.RUNNING, false, null,
                List.of(
                    // Filter 1: Must be transform "order-processing" with action "validate"
                    new ErrorSourceFilter(FlowType.TRANSFORM, "order-processing", "validate", null),
                    // Filter 2: Must be any ValidationAction class
                    new ErrorSourceFilter(null, null, null, "com.example.ValidationAction"),
                    // Filter 3: Must be data sink "error-sink"
                    new ErrorSourceFilter(FlowType.DATA_SINK, "error-sink", null, null)
                ), null, null, null, null);
        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of(complexDataSource));

        // Should match filter 1
        List<OnErrorDataSource> triggered = onErrorDataSourceService.getTriggeredDataSources(
                "order-processing", FlowType.TRANSFORM, "validate", "com.company.OrderValidator", "error", Map.of(), Map.of());
        assertThat(triggered).hasSize(1);

        // Should match filter 2
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "any-flow", FlowType.TRANSFORM, "any-action", "com.example.ValidationAction", "error", Map.of(), Map.of());
        assertThat(triggered).hasSize(1);

        // Should match filter 3
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "error-sink", FlowType.DATA_SINK, "any-action", "com.company.EgressAction", "error", Map.of(), Map.of());
        assertThat(triggered).hasSize(1);

        // Should not match any filter - filter 1 fails on flow name, filter 2 fails on class, filter 3 fails on type
        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "other-flow", FlowType.DATA_SINK, "validate", "com.company.OrderValidator", "error", Map.of(), Map.of());
        assertThat(triggered).isEmpty();
    }

    @Test
    void testMetadataFiltering() {
        OnErrorDataSource metadataDataSource = onErrorDataSource("metadata", FlowState.RUNNING, false, null, 
                null, List.of(new KeyValue("env", "prod")), null, null, null);
        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of(metadataDataSource));

        List<OnErrorDataSource> triggered = onErrorDataSourceService.getTriggeredDataSources(
                "flow", FlowType.TRANSFORM, "action", "com.example.ActionClass", "error", Map.of("env", "prod"), Map.of());
        assertThat(triggered).hasSize(1);

        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "flow", FlowType.TRANSFORM, "action", "com.example.ActionClass", "error", Map.of("env", "dev"), Map.of());
        assertThat(triggered).isEmpty();
    }

    @Test
    void testAnnotationFiltering() {
        OnErrorDataSource annotationDataSource = onErrorDataSource("annotation", FlowState.RUNNING, false, null, 
                null, null, List.of(new KeyValue("priority", "high")), null, null);
        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of(annotationDataSource));

        List<OnErrorDataSource> triggered = onErrorDataSourceService.getTriggeredDataSources(
                "flow", FlowType.TRANSFORM, "action", "com.example.ActionClass", "error", Map.of(), Map.of("priority", "high"));
        assertThat(triggered).hasSize(1);

        triggered = onErrorDataSourceService.getTriggeredDataSources(
                "flow", FlowType.TRANSFORM, "action", "com.example.ActionClass", "error", Map.of(), Map.of("priority", "low"));
        assertThat(triggered).isEmpty();
    }

    @Test
    void testUpdateSnapshot() {
        List<Flow> flows = new ArrayList<>();
        flows.add(onErrorDataSource("errorFlow1", FlowState.RUNNING, false, ".*error.*", null, null, null, null, null));
        flows.add(onErrorDataSource("errorFlow2", FlowState.STOPPED, true, ".*exception.*", null, null, null, null, null));

        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(flows);

        Snapshot snapshot = new Snapshot();
        onErrorDataSourceService.updateSnapshot(snapshot);

        assertThat(snapshot.getOnErrorDataSources()).hasSize(2);

        Map<String, OnErrorDataSourceSnapshot> onErrorDataSourceSnapshotMap = snapshot.getOnErrorDataSources().stream()
                .collect(Collectors.toMap(FlowSnapshot::getName, Function.identity()));

        OnErrorDataSourceSnapshot errorFlow1Snapshot = onErrorDataSourceSnapshotMap.get("errorFlow1");
        assertThat(errorFlow1Snapshot.isRunning()).isTrue();
        assertThat(errorFlow1Snapshot.isTestMode()).isFalse();

        OnErrorDataSourceSnapshot errorFlow2Snapshot = onErrorDataSourceSnapshotMap.get("errorFlow2");
        assertThat(errorFlow2Snapshot.isRunning()).isFalse();
        assertThat(errorFlow2Snapshot.isTestMode()).isTrue();
    }

    @Test
    void testResetFromSnapshot() {
        OnErrorDataSource running = onErrorDataSource("running", FlowState.RUNNING, true, ".*error.*", null, null, null, null, null);
        OnErrorDataSource stopped = onErrorDataSource("stopped", FlowState.STOPPED, false, ".*exception.*", null, null, null, null, null);

        Snapshot snapshot = new Snapshot();

        List<OnErrorDataSourceSnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot("running", true, false));
        snapshots.add(snapshot("stopped", true, true));
        snapshots.add(snapshot("missing", false, true));
        snapshot.setOnErrorDataSources(snapshots);

        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of(running, stopped));

        Result result = onErrorDataSourceService.resetFromSnapshot(snapshot, true);

        Mockito.verify(onErrorDataSourceRepo, Mockito.times(2)).saveAll(flowCaptor.capture());

        // First call is for updated flows, second is for placeholders
        List<Collection<OnErrorDataSource>> allSaves = flowCaptor.getAllValues();
        Map<String, DataSource> updatedFlows = allSaves.get(0).stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));

        assertThat(updatedFlows).hasSize(2);

        OnErrorDataSource updatedRunning = getAsOnErrorDataSource(updatedFlows, "running");
        OnErrorDataSource updatedStopped = getAsOnErrorDataSource(updatedFlows, "stopped");

        assertThat(updatedRunning).isNotNull();
        assertThat(updatedRunning.isRunning()).isTrue();
        assertThat(updatedRunning.isTestMode()).isFalse();
        assertThat(updatedRunning.getErrorMessageRegex()).isEqualTo(".*error.*");

        assertThat(updatedStopped).isNotNull();
        assertThat(updatedStopped.isRunning()).isTrue();
        assertThat(updatedStopped.isTestMode()).isTrue();
        assertThat(updatedStopped.getErrorMessageRegex()).isEqualTo(".*exception.*");

        // verify placeholder was created for missing flow (with system-plugin since no sourcePlugin in old snapshot)
        Collection<OnErrorDataSource> placeholders = allSaves.get(1);
        assertThat(placeholders).hasSize(1);
        OnErrorDataSource missingPlaceholder = placeholders.iterator().next();
        assertThat(missingPlaceholder.getName()).isEqualTo("missing");
        assertThat(missingPlaceholder.getFlowStatus().getPlaceholder()).isTrue();
        assertThat(missingPlaceholder.isRunning()).isFalse();
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

        OnErrorDataSourceSnapshot runningSnapshot = new OnErrorDataSourceSnapshot("pending-running");
        runningSnapshot.setRunning(true);
        runningSnapshot.setTestMode(false);
        runningSnapshot.setSourcePlugin(pluginCoordinates);
        runningSnapshot.setTopic("test-topic");
        runningSnapshot.setMaxErrors(10);

        OnErrorDataSourceSnapshot stoppedSnapshot = new OnErrorDataSourceSnapshot("pending-stopped");
        stoppedSnapshot.setRunning(false);
        stoppedSnapshot.setTestMode(true);
        stoppedSnapshot.setSourcePlugin(pluginCoordinates);

        Snapshot snapshot = new Snapshot();
        snapshot.setOnErrorDataSources(List.of(runningSnapshot, stoppedSnapshot));

        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of());

        Result result = onErrorDataSourceService.resetFromSnapshot(snapshot, true);

        Mockito.verify(onErrorDataSourceRepo).saveAll(flowCaptor.capture());

        Map<String, OnErrorDataSource> savedFlows = flowCaptor.getValue().stream()
                .collect(Collectors.toMap(Flow::getName, f -> (OnErrorDataSource) f));

        assertThat(savedFlows).hasSize(2);

        // Verify running placeholder
        OnErrorDataSource runningPlaceholder = savedFlows.get("pending-running");
        assertThat(runningPlaceholder).isNotNull();
        assertThat(runningPlaceholder.isRunning()).isTrue();
        assertThat(runningPlaceholder.isTestMode()).isFalse();
        assertThat(runningPlaceholder.isInvalid()).isTrue();
        assertThat(runningPlaceholder.getSourcePlugin()).isEqualTo(pluginCoordinates);
        assertThat(runningPlaceholder.getTopic()).isEqualTo("test-topic");
        assertThat(runningPlaceholder.getMaxErrors()).isEqualTo(10);
        assertThat(runningPlaceholder.getFlowStatus().getErrors()).hasSize(1);
        assertThat(runningPlaceholder.getFlowStatus().getErrors().get(0).getErrorType()).isEqualTo(FlowErrorType.INVALID_CONFIG);
        assertThat(runningPlaceholder.getFlowStatus().getErrors().get(0).getMessage()).contains("Waiting for plugin");

        // Verify stopped placeholder with testMode
        OnErrorDataSource stoppedPlaceholder = savedFlows.get("pending-stopped");
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

    @Test
    void testDataSourceErrorsExceeded() {
        setupErrorExceeded();
        List<DataSourceErrorState> errorStates = onErrorDataSourceService.dataSourceErrorsExceeded();
        assertEquals(2, errorStates.size());
        assertEquals(new DataSourceErrorState("flow1", 1, 0), errorStates.get(0));
        assertEquals(new DataSourceErrorState("flow3", 6, 5), errorStates.get(1));
    }

    void setupErrorExceeded() {
        OnErrorDataSource flow1 = onErrorDataSource("flow1", FlowState.RUNNING, false, ".*error.*", null, null, null, null, null);
        flow1.setMaxErrors(0);
        OnErrorDataSource flow2 = onErrorDataSource("flow2", FlowState.RUNNING, false, ".*error.*", null, null, null, null, null);
        flow2.setMaxErrors(5);
        OnErrorDataSource flow3 = onErrorDataSource("flow3", FlowState.RUNNING, false, ".*error.*", null, null, null, null, null);
        flow3.setMaxErrors(5);
        OnErrorDataSource flow4 = onErrorDataSource("flow4", FlowState.STOPPED, false, ".*error.*", null, null, null, null, null);
        flow4.setMaxErrors(5);

        Mockito.when(flowCacheService.flowsOfType(FlowType.ON_ERROR_DATA_SOURCE)).thenReturn(List.of(flow1, flow2, flow3, flow4));

        Mockito.when(errorCountService.errorsForFlow(FlowType.ON_ERROR_DATA_SOURCE, "flow1")).thenReturn(1);
        Mockito.when(errorCountService.errorsForFlow(FlowType.ON_ERROR_DATA_SOURCE, "flow2")).thenReturn(5);
        Mockito.when(errorCountService.errorsForFlow(FlowType.ON_ERROR_DATA_SOURCE, "flow3")).thenReturn(6);

        onErrorDataSourceService.refreshCache();
    }

    private OnErrorDataSource getAsOnErrorDataSource(Map<String, DataSource> dataSourceMap, String name) {
        DataSource dataSource = dataSourceMap.get(name);
        if (dataSource instanceof OnErrorDataSource onErrorDataSource) {
            return onErrorDataSource;
        }

        Assertions.fail("Invalid data source type " + dataSource.getType());
        return null;
    }

    OnErrorDataSource onErrorDataSource(String name, FlowState flowState, boolean testMode, String errorMessageRegex,
                                       List<ErrorSourceFilter> sourceFilters,
                                       List<KeyValue> metadataFilters, List<KeyValue> annotationFilters,
                                       List<String> includeSourceMetadataRegex, List<String> includeSourceAnnotationsRegex) {
        OnErrorDataSource dataSource = new OnErrorDataSource();
        dataSource.setName(name);
        dataSource.setMaxErrors(-1);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        flowStatus.setTestMode(testMode);
        dataSource.setFlowStatus(flowStatus);
        dataSource.setErrorMessageRegex(errorMessageRegex);
        dataSource.setSourceFilters(sourceFilters);
        dataSource.setMetadataFilters(metadataFilters);
        dataSource.setAnnotationFilters(annotationFilters);
        dataSource.setIncludeSourceMetadataRegex(includeSourceMetadataRegex);
        dataSource.setIncludeSourceAnnotationsRegex(includeSourceAnnotationsRegex);
        dataSource.setSourcePlugin(PluginCoordinates.builder().artifactId("art").groupId("group").version("ver").build());
        return dataSource;
    }

    OnErrorDataSourceSnapshot snapshot(String name, boolean running, boolean testMode) {
        OnErrorDataSourceSnapshot snapshot = new OnErrorDataSourceSnapshot(name);
        snapshot.setRunning(running);
        snapshot.setTestMode(testMode);
        snapshot.setMaxErrors(-1);
        return snapshot;
    }
}