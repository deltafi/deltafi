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

import tools.jackson.databind.ObjectMapper;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.types.Property;
import org.deltafi.core.types.PropertySet;
import org.deltafi.core.types.leader.ConfigDiff;
import org.deltafi.core.types.leader.DiffItem;
import org.deltafi.core.types.leader.DiffSection;
import org.deltafi.core.types.leader.DiffType;
import org.deltafi.core.types.snapshot.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaderConfigServiceTest {

    @Mock
    private DeltaFiPropertiesService deltaFiPropertiesService;

    @Mock
    private SystemSnapshotService systemSnapshotService;

    @Mock
    private HttpClient httpClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DeltaFiProperties deltaFiProperties;

    private LeaderConfigService leaderConfigService;

    @BeforeEach
    void setUp() {
        lenient().when(deltaFiProperties.getMemberPollingTimeout()).thenReturn(5000);
        lenient().when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of());
        lenient().when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        lenient().when(deltaFiPropertiesService.getPopulatedProperties(false)).thenReturn(List.of(createPropertySet(
                createProperty("systemName", "DeltaFi", null),
                createProperty("allowedAnalyticsAnnotations", null, VariableDataType.SET),
                createProperty("checkContentStoragePercentThreshold", "90", VariableDataType.NUMBER)
        )));

        leaderConfigService = new LeaderConfigService(
                deltaFiPropertiesService,
                systemSnapshotService,
                httpClient,
                objectMapper
        );
    }

    @Test
    void computeDiff_identicalSnapshots_returnsNoDifferences() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(createPlugin("org.deltafi", "plugin-a", "1.0.0")),
                List.of(createDataSink("sink1", true, false)),
                List.of(createTransformFlow("transform1", true, false)),
                List.of(new KeyValue("key1", "value1"))
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(createPlugin("org.deltafi", "plugin-a", "1.0.0")),
                List.of(createDataSink("sink1", true, false)),
                List.of(createTransformFlow("transform1", true, false)),
                List.of(new KeyValue("key1", "value1"))
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        assertEquals(0, diff.totalDiffCount());
    }

    @Test
    void computeDiff_pluginVersionDifference_detectsModified() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(createPlugin("org.deltafi", "plugin-a", "1.0.0")),
                List.of(), List.of(), List.of()
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(createPlugin("org.deltafi", "plugin-a", "2.0.0")),
                List.of(), List.of(), List.of()
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        DiffSection pluginSection = findSection(diff, "plugins");
        assertNotNull(pluginSection);
        assertEquals(1, pluginSection.diffCount());

        DiffItem item = pluginSection.diffs().get(0);
        assertEquals(DiffType.MODIFIED, item.type());
        assertEquals("1.0.0", item.leaderValue());
        assertEquals("2.0.0", item.memberValue());
    }

    @Test
    void computeDiff_pluginMissingOnMember_detectsRemoved() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(
                        createPlugin("org.deltafi", "plugin-a", "1.0.0"),
                        createPlugin("org.deltafi", "plugin-b", "1.0.0")
                ),
                List.of(), List.of(), List.of()
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(createPlugin("org.deltafi", "plugin-a", "1.0.0")),
                List.of(), List.of(), List.of()
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        DiffSection pluginSection = findSection(diff, "plugins");
        assertNotNull(pluginSection);
        assertEquals(1, pluginSection.diffCount());

        DiffItem item = pluginSection.diffs().get(0);
        assertEquals(DiffType.REMOVED, item.type());
        assertTrue(item.path().contains("plugin-b"));
    }

    @Test
    void computeDiff_pluginAddedOnMember_detectsAdded() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(createPlugin("org.deltafi", "plugin-a", "1.0.0")),
                List.of(), List.of(), List.of()
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(
                        createPlugin("org.deltafi", "plugin-a", "1.0.0"),
                        createPlugin("org.deltafi", "plugin-extra", "1.0.0")
                ),
                List.of(), List.of(), List.of()
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        DiffSection pluginSection = findSection(diff, "plugins");
        assertNotNull(pluginSection);
        assertEquals(1, pluginSection.diffCount());

        DiffItem item = pluginSection.diffs().get(0);
        assertEquals(DiffType.ADDED, item.type());
        assertTrue(item.path().contains("plugin-extra"));
    }

    @Test
    void computeDiff_dataSinkRunningDifference_detectsModified() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(),
                List.of(createDataSink("sink1", true, false)),
                List.of(), List.of()
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(),
                List.of(createDataSink("sink1", false, false)),
                List.of(), List.of()
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        DiffSection sinkSection = findSection(diff, "dataSinks");
        assertNotNull(sinkSection);
        assertEquals(1, sinkSection.diffCount());

        DiffItem item = sinkSection.diffs().get(0);
        assertEquals(DiffType.MODIFIED, item.type());
        assertEquals("dataSinks.sink1.running", item.path());
        assertEquals(true, item.leaderValue());
        assertEquals(false, item.memberValue());
    }

    @Test
    void computeDiff_dataSinkTestModeDifference_detectsModified() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(),
                List.of(createDataSink("sink1", true, false)),
                List.of(), List.of()
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(),
                List.of(createDataSink("sink1", true, true)),
                List.of(), List.of()
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        DiffSection sinkSection = findSection(diff, "dataSinks");
        assertNotNull(sinkSection);
        assertEquals(1, sinkSection.diffCount());

        DiffItem item = sinkSection.diffs().get(0);
        assertEquals(DiffType.MODIFIED, item.type());
        assertEquals("dataSinks.sink1.testMode", item.path());
    }

    @Test
    void computeDiff_propertyDifference_detectsModified() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(), List.of(), List.of(),
                List.of(new KeyValue("systemName", "Leader System"))
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(), List.of(), List.of(),
                List.of(new KeyValue("systemName", "Member System"))
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        DiffSection propSection = findSection(diff, "deltaFiProperties");
        assertNotNull(propSection);
        assertEquals(1, propSection.diffCount());

        DiffItem item = propSection.diffs().get(0);
        assertEquals(DiffType.MODIFIED, item.type());
        assertEquals("Leader System", item.leaderValue());
        assertEquals("Member System", item.memberValue());
    }

    @Test
    void computeDiff_propertyMissingOnMember_detectsRemoved() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(), List.of(), List.of(),
                List.of(
                        new KeyValue("key1", "value1"),
                        new KeyValue("key2", "value2")
                )
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(), List.of(), List.of(),
                List.of(new KeyValue("key1", "value1"))
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        DiffSection propSection = findSection(diff, "deltaFiProperties");
        assertNotNull(propSection);
        assertEquals(1, propSection.diffCount());

        DiffItem item = propSection.diffs().get(0);
        assertEquals(DiffType.REMOVED, item.type());
        assertTrue(item.path().contains("key2"));
    }

    @Test
    void computeDiff_propertyAddedOnMember_detectsAdded() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(), List.of(), List.of(),
                List.of(new KeyValue("key1", "value1"))
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(), List.of(), List.of(),
                List.of(
                        new KeyValue("key1", "value1"),
                        new KeyValue("newKey", "newValue")
                )
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        DiffSection propSection = findSection(diff, "deltaFiProperties");
        assertNotNull(propSection);
        assertEquals(1, propSection.diffCount());

        DiffItem item = propSection.diffs().get(0);
        assertEquals(DiffType.ADDED, item.type());
        assertTrue(item.path().contains("newKey"));
    }

    @Test
    void computeDiff_transformFlowDifferences_detected() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(), List.of(),
                List.of(createTransformFlow("flow1", true, false)),
                List.of()
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(), List.of(),
                List.of(createTransformFlow("flow1", false, true)),
                List.of()
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        DiffSection flowSection = findSection(diff, "transformFlows");
        assertNotNull(flowSection);
        assertEquals(2, flowSection.diffCount()); // running and testMode both different
    }

    @Test
    void computeDiff_multipleDifferencesAcrossSections_aggregatesCorrectly() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(createPlugin("org.deltafi", "plugin-a", "1.0.0")),
                List.of(createDataSink("sink1", true, false)),
                List.of(createTransformFlow("transform1", true, false)),
                List.of(new KeyValue("key1", "value1"))
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(createPlugin("org.deltafi", "plugin-a", "2.0.0")),
                List.of(createDataSink("sink1", false, false)),
                List.of(createTransformFlow("transform1", true, true)),
                List.of(new KeyValue("key1", "different"))
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        assertEquals(4, diff.totalDiffCount());
        assertEquals(4, diff.sections().size());
    }

    @Test
    void computeDiff_setPropertySameValuesDifferentOrder_noDifference() {
        // SET-typed properties should be normalized before snapshot creation,
        // so identical values in different order should produce matching snapshot values
        Snapshot leaderSnapshot = createSnapshot(
                List.of(), List.of(), List.of(),
                List.of(new KeyValue("allowedAnalyticsAnnotations", "a, b, c"))
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(), List.of(), List.of(),
                List.of(new KeyValue("allowedAnalyticsAnnotations", "a, b, c"))
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        assertEquals(0, diff.totalDiffCount());
    }

    @Test
    void computeDiff_knownDefaultMissingOnLeader_showsDefaultValue() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(), List.of(), List.of(), List.of()
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(), List.of(), List.of(),
                List.of(new KeyValue("checkContentStoragePercentThreshold", "89"))
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        DiffSection propSection = findSection(diff, "deltaFiProperties");
        assertNotNull(propSection);
        assertEquals(1, propSection.diffCount());

        DiffItem item = propSection.diffs().get(0);
        assertEquals(DiffType.MODIFIED, item.type());
        assertEquals("90", item.leaderValue());
        assertEquals("89", item.memberValue());
    }

    @Test
    void computeDiff_knownDefaultMissingOnMember_showsDefaultValue() {
        Snapshot leaderSnapshot = createSnapshot(
                List.of(), List.of(), List.of(),
                List.of(new KeyValue("checkContentStoragePercentThreshold", "89"))
        );

        Snapshot memberSnapshot = createSnapshot(
                List.of(), List.of(), List.of(), List.of()
        );

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        DiffSection propSection = findSection(diff, "deltaFiProperties");
        assertNotNull(propSection);
        assertEquals(1, propSection.diffCount());

        DiffItem item = propSection.diffs().get(0);
        assertEquals(DiffType.MODIFIED, item.type());
        assertEquals("89", item.leaderValue());
        assertEquals("90", item.memberValue());
    }

    @Test
    void computeDiff_nullLists_handledGracefully() {
        Snapshot leaderSnapshot = new Snapshot();
        Snapshot memberSnapshot = new Snapshot();

        ConfigDiff diff = leaderConfigService.computeDiffForTest(leaderSnapshot, memberSnapshot);

        assertEquals(0, diff.totalDiffCount());
    }

    private DiffSection findSection(ConfigDiff diff, String sectionName) {
        return diff.sections().stream()
                .filter(s -> s.name().equals(sectionName))
                .findFirst()
                .orElse(null);
    }

    private Snapshot createSnapshot(List<PluginSnapshot> plugins, List<DataSinkSnapshot> dataSinks,
                                    List<TransformFlowSnapshot> transforms, List<KeyValue> properties) {
        Snapshot snapshot = new Snapshot();
        snapshot.setPlugins(plugins);
        snapshot.setDataSinks(dataSinks);
        snapshot.setTransformFlows(transforms);
        snapshot.setDeltaFiProperties(properties);
        snapshot.setRestDataSources(List.of());
        snapshot.setTimedDataSources(List.of());
        return snapshot;
    }

    private PluginSnapshot createPlugin(String groupId, String artifactId, String version) {
        return new PluginSnapshot(
                "image:" + version,
                null,
                PluginCoordinates.builder()
                        .groupId(groupId)
                        .artifactId(artifactId)
                        .version(version)
                        .build()
        );
    }

    private DataSinkSnapshot createDataSink(String name, boolean running, boolean testMode) {
        DataSinkSnapshot sink = new DataSinkSnapshot();
        sink.setName(name);
        sink.setRunning(running);
        sink.setTestMode(testMode);
        return sink;
    }

    private TransformFlowSnapshot createTransformFlow(String name, boolean running, boolean testMode) {
        TransformFlowSnapshot flow = new TransformFlowSnapshot();
        flow.setName(name);
        flow.setRunning(running);
        flow.setTestMode(testMode);
        return flow;
    }

    private PropertySet createPropertySet(Property... properties) {
        PropertySet propertySet = new PropertySet();
        propertySet.setProperties(List.of(properties));
        return propertySet;
    }

    private Property createProperty(String key, String defaultValue, VariableDataType dataType) {
        return Property.builder()
                .key(key)
                .defaultValue(defaultValue)
                .dataType(dataType)
                .build();
    }
}
