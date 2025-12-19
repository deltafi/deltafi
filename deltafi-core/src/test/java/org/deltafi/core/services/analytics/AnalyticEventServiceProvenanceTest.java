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
package org.deltafi.core.services.analytics;

import org.deltafi.common.types.DeltaFileFlowState;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.repo.AnalyticsRepo;
import org.deltafi.core.repo.EventAnnotationsRepo;
import org.deltafi.core.services.*;
import org.deltafi.core.types.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticEventServiceProvenanceTest {

    @Mock private AnalyticsRepo analyticsRepo;
    @Mock private EventAnnotationsRepo eventAnnotationsRepo;
    @Mock private FlowDefinitionService flowDefinitionService;
    @Mock private EventGroupService eventGroupService;
    @Mock private AnnotationKeyService annotationKeyService;
    @Mock private AnnotationValueService annotationValueService;
    @Mock private ActionNameService actionNameService;
    @Mock private ErrorCauseService errorCauseService;
    @Mock private DeltaFiPropertiesService deltaFiPropertiesService;
    @Mock private AnalyticsClient analyticsClient;
    @Mock private ProvenanceClient provenanceClient;
    @Mock private DeltaFiProperties deltaFiProperties;

    private AnalyticEventService analyticEventService;

    @BeforeEach
    void setUp() {
        analyticEventService = new AnalyticEventService(
                analyticsRepo, eventAnnotationsRepo, flowDefinitionService,
                eventGroupService, annotationKeyService, annotationValueService,
                actionNameService, errorCauseService, deltaFiPropertiesService,
                analyticsClient, provenanceClient
        );
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
    }

    @Test
    void recordProvenance_doesNothingWhenDisabled() {
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(false);

        DeltaFile deltaFile = createTestDeltaFile();
        DeltaFileFlow flow = deltaFile.getFlows().iterator().next();

        analyticEventService.recordProvenance(deltaFile, flow);

        verifyNoInteractions(provenanceClient);
    }

    @Test
    void recordProvenance_sendsRecordWhenEnabled() {
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(true);
        when(deltaFiProperties.getSystemName()).thenReturn("test-system");
        when(deltaFiProperties.provenanceAnnotationsAllowedList()).thenReturn(List.of("customer"));

        DeltaFile deltaFile = createTestDeltaFile();
        deltaFile.addAnnotations(Map.of("customer", "ABC123", "internal", "should-not-appear"));
        DeltaFileFlow flow = deltaFile.getFlows().iterator().next();
        flow.setState(DeltaFileFlowState.COMPLETE);

        analyticEventService.recordProvenance(deltaFile, flow);

        ArgumentCaptor<ProvenanceClient.ProvenanceRecord> captor =
                ArgumentCaptor.forClass(ProvenanceClient.ProvenanceRecord.class);
        verify(provenanceClient).writeRecord(captor.capture());

        ProvenanceClient.ProvenanceRecord record = captor.getValue();
        assertEquals(deltaFile.getDid().toString(), record.did());
        assertNull(record.parentDid());  // No parent for non-split file
        assertEquals("test-system", record.systemName());
        assertEquals("TestDataSource", record.dataSource());
        assertEquals("test-file.txt", record.filename());
        assertEquals("COMPLETE", record.finalState());
        assertEquals(Map.of("customer", "ABC123"), record.annotations());
    }

    @Test
    void recordProvenance_setsDataSinkForDataSinkFlows() {
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(true);
        when(deltaFiProperties.getSystemName()).thenReturn("system");
        // No need to stub provenanceAnnotationsAllowedList - empty annotations returns early

        DeltaFile deltaFile = createTestDeltaFile();
        DeltaFileFlow sinkFlow = createFlow("MySink", FlowType.DATA_SINK, DeltaFileFlowState.COMPLETE);
        deltaFile.getFlows().add(sinkFlow);

        analyticEventService.recordProvenance(deltaFile, sinkFlow);

        ArgumentCaptor<ProvenanceClient.ProvenanceRecord> captor =
                ArgumentCaptor.forClass(ProvenanceClient.ProvenanceRecord.class);
        verify(provenanceClient).writeRecord(captor.capture());

        assertEquals("MySink", captor.getValue().dataSink());
    }

    @Test
    void recordProvenance_nullDataSinkForNonDataSinkFlows() {
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(true);
        when(deltaFiProperties.getSystemName()).thenReturn("system");
        // No need to stub provenanceAnnotationsAllowedList - empty annotations returns early

        DeltaFile deltaFile = createTestDeltaFile();
        DeltaFileFlow transformFlow = createFlow("MyTransform", FlowType.TRANSFORM, DeltaFileFlowState.ERROR);
        deltaFile.getFlows().add(transformFlow);

        analyticEventService.recordProvenance(deltaFile, transformFlow);

        ArgumentCaptor<ProvenanceClient.ProvenanceRecord> captor =
                ArgumentCaptor.forClass(ProvenanceClient.ProvenanceRecord.class);
        verify(provenanceClient).writeRecord(captor.capture());

        assertNull(captor.getValue().dataSink());
        assertEquals("ERROR", captor.getValue().finalState());
    }

    @Test
    void recordProvenanceForCancel_recordsAllCancelledFlows() {
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(true);
        when(deltaFiProperties.getSystemName()).thenReturn("system");
        // No need to stub provenanceAnnotationsAllowedList - empty annotations returns early

        DeltaFile deltaFile = createTestDeltaFile();
        DeltaFileFlow completedFlow = deltaFile.getFlows().iterator().next();
        completedFlow.setState(DeltaFileFlowState.COMPLETE);

        DeltaFileFlow cancelledFlow1 = createFlow("CancelledSink1", FlowType.DATA_SINK, DeltaFileFlowState.CANCELLED);
        DeltaFileFlow cancelledFlow2 = createFlow("CancelledSink2", FlowType.DATA_SINK, DeltaFileFlowState.CANCELLED);
        deltaFile.getFlows().add(cancelledFlow1);
        deltaFile.getFlows().add(cancelledFlow2);

        analyticEventService.recordProvenanceForCancel(deltaFile);

        // Should record 2 provenance records (one for each cancelled flow)
        verify(provenanceClient, times(2)).writeRecord(any());
    }

    @Test
    void recordProvenanceForCancel_doesNothingWhenDisabled() {
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(false);

        DeltaFile deltaFile = createTestDeltaFile();
        DeltaFileFlow cancelledFlow = createFlow("CancelledSink", FlowType.DATA_SINK, DeltaFileFlowState.CANCELLED);
        deltaFile.getFlows().add(cancelledFlow);

        analyticEventService.recordProvenanceForCancel(deltaFile);

        verifyNoInteractions(provenanceClient);
    }

    @Test
    void recordProvenance_filtersAnnotationsByAllowList() {
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(true);
        when(deltaFiProperties.getSystemName()).thenReturn("system");
        when(deltaFiProperties.provenanceAnnotationsAllowedList()).thenReturn(List.of("allowed1", "allowed2"));

        DeltaFile deltaFile = createTestDeltaFile();
        deltaFile.addAnnotations(Map.of("allowed1", "value1", "allowed2", "value2", "notAllowed", "value3"));
        DeltaFileFlow flow = deltaFile.getFlows().iterator().next();

        analyticEventService.recordProvenance(deltaFile, flow);

        ArgumentCaptor<ProvenanceClient.ProvenanceRecord> captor =
                ArgumentCaptor.forClass(ProvenanceClient.ProvenanceRecord.class);
        verify(provenanceClient).writeRecord(captor.capture());

        Map<String, String> annotations = captor.getValue().annotations();
        assertEquals(2, annotations.size());
        assertEquals("value1", annotations.get("allowed1"));
        assertEquals("value2", annotations.get("allowed2"));
        assertNull(annotations.get("notAllowed"));
    }

    @Test
    void recordProvenance_emptyAnnotationsWhenNoAllowList() {
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(true);
        when(deltaFiProperties.getSystemName()).thenReturn("system");
        when(deltaFiProperties.provenanceAnnotationsAllowedList()).thenReturn(List.of());

        DeltaFile deltaFile = createTestDeltaFile();
        deltaFile.addAnnotations(Map.of("key1", "value1", "key2", "value2"));
        DeltaFileFlow flow = deltaFile.getFlows().iterator().next();

        analyticEventService.recordProvenance(deltaFile, flow);

        ArgumentCaptor<ProvenanceClient.ProvenanceRecord> captor =
                ArgumentCaptor.forClass(ProvenanceClient.ProvenanceRecord.class);
        verify(provenanceClient).writeRecord(captor.capture());

        assertTrue(captor.getValue().annotations().isEmpty());
    }

    @Test
    void recordProvenanceForSplit_recordsSplitState() {
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(true);
        when(deltaFiProperties.getSystemName()).thenReturn("system");

        DeltaFile deltaFile = createTestDeltaFile();
        DeltaFileFlow transformFlow = createFlow("MyTransform", FlowType.TRANSFORM, DeltaFileFlowState.IN_FLIGHT);
        deltaFile.getFlows().add(transformFlow);

        analyticEventService.recordProvenanceForSplit(deltaFile, transformFlow);

        ArgumentCaptor<ProvenanceClient.ProvenanceRecord> captor =
                ArgumentCaptor.forClass(ProvenanceClient.ProvenanceRecord.class);
        verify(provenanceClient).writeRecord(captor.capture());

        ProvenanceClient.ProvenanceRecord record = captor.getValue();
        assertEquals(deltaFile.getDid().toString(), record.did());
        assertNull(record.parentDid());  // No parent for this test file
        assertEquals("SPLIT", record.finalState());
        assertNull(record.dataSink());
    }

    @Test
    void recordProvenance_includesParentDidForSplitChild() {
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(true);
        when(deltaFiProperties.getSystemName()).thenReturn("system");
        // No need to stub provenanceAnnotationsAllowedList - empty annotations returns early

        UUID parentDid = UUID.randomUUID();
        DeltaFile deltaFile = createTestDeltaFile();
        deltaFile.setParentDids(List.of(parentDid));
        DeltaFileFlow flow = deltaFile.getFlows().iterator().next();
        flow.setState(DeltaFileFlowState.COMPLETE);

        analyticEventService.recordProvenance(deltaFile, flow);

        ArgumentCaptor<ProvenanceClient.ProvenanceRecord> captor =
                ArgumentCaptor.forClass(ProvenanceClient.ProvenanceRecord.class);
        verify(provenanceClient).writeRecord(captor.capture());

        ProvenanceClient.ProvenanceRecord record = captor.getValue();
        assertEquals(deltaFile.getDid().toString(), record.did());
        assertEquals(parentDid.toString(), record.parentDid());
    }

    @Test
    void recordProvenanceForSplit_doesNothingWhenDisabled() {
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(false);

        DeltaFile deltaFile = createTestDeltaFile();
        DeltaFileFlow flow = deltaFile.getFlows().iterator().next();

        analyticEventService.recordProvenanceForSplit(deltaFile, flow);

        verifyNoInteractions(provenanceClient);
    }

    private DeltaFile createTestDeltaFile() {
        DeltaFileFlow dataSourceFlow = createFlow("TestDataSource", FlowType.TIMED_DATA_SOURCE, DeltaFileFlowState.COMPLETE);

        DeltaFile deltaFile = DeltaFile.builder()
                .did(UUID.randomUUID())
                .dataSource("TestDataSource")
                .name("test-file.txt")
                .created(OffsetDateTime.now())
                .flows(new LinkedHashSet<>(List.of(dataSourceFlow)))
                .annotations(new LinkedHashSet<>())
                .build();

        deltaFile.wireBackPointers();
        return deltaFile;
    }

    private DeltaFileFlow createFlow(String name, FlowType type, DeltaFileFlowState state) {
        FlowDefinition flowDefinition = FlowDefinition.builder()
                .name(name)
                .type(type)
                .build();

        return DeltaFileFlow.builder()
                .flowDefinition(flowDefinition)
                .state(state)
                .modified(OffsetDateTime.now())
                .input(new DeltaFileFlowInput())
                .build();
    }
}
