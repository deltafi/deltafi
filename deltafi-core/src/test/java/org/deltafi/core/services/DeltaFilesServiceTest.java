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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.assertj.core.api.Assertions;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.test.uuid.TestUUIDGenerator;
import org.deltafi.common.types.*;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.exceptions.MissingFlowException;
import org.deltafi.core.generated.types.DeltaFilesFilter;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.RetryResult;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.repo.*;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.types.*;
import org.deltafi.core.util.FlowBuilders;
import org.deltafi.core.util.MockFlowDefinitionService;
import org.deltafi.core.util.ParameterResolver;
import org.deltafi.core.util.UtilService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeltaFilesServiceTest {
    private static final UUID DID = UUID.randomUUID();
    private final TestClock testClock = new TestClock();

    private final TimedDataSourceService timedDataSourceService;
    private final TransformFlowService transformFlowService;
    private final RestDataSourceService restDataSourceService;
    private final OnErrorDataSourceService onErrorDataSourceService;
    private final DataSinkService dataSinkService;
    private final StateMachine stateMachine;
    private final DeltaFileRepo deltaFileRepo;
    private final DeltaFileFlowRepo deltaFileFlowRepo;
    private final CoreEventQueue coreEventQueue;
    private final ContentStorageService contentStorageService;
    private final ResumePolicyService resumePolicyService;
    private final DeltaFileCacheService deltaFileCacheService;
    private final QueueManagementService queueManagementService;
    private final QueuedAnnotationRepo queuedAnnotationRepo;
    private final DeltaFilesService deltaFilesService;
    private final MetricService metricService;
    private final UtilService utilService;
    private final FlowDefinitionService flowDefinitionService = new MockFlowDefinitionService();
    private final AnalyticEventService analyticEventService;

    @Captor
    ArgumentCaptor<DeltaFile> deltaFileCaptor;
    @Captor
    ArgumentCaptor<List<WrappedActionInput>> actionInputListCaptor;
    @Captor
    ArgumentCaptor<List<StateMachineInput>> stateMachineInputCaptor;
    @Captor
    ArgumentCaptor<QueuedAnnotation> queuedAnnotationCaptor;
    @Captor
    private ArgumentCaptor<List<UUID>> uuidListCaptor;
    @Captor
    private ArgumentCaptor<List<String>> stringListCaptor;
    @Captor
    ArgumentCaptor<List<QueuedAnnotation>> queuedAnnotationListCaptor;

    DeltaFilesServiceTest(@Mock TransformFlowService transformFlowService,
                          @Mock DataSinkService dataSinkService, @Mock StateMachine stateMachine,
                          @Mock AnnotationRepo annotationRepo, @Mock DeltaFileRepo deltaFileRepo,
                          @Mock DeltaFileFlowRepo deltaFileFlowRepo,
                          @Mock CoreEventQueue coreEventQueue, @Mock ContentStorageService contentStorageService,
                          @Mock ResumePolicyService resumePolicyService, @Mock MetricService metricService,
                          @Mock AnalyticEventService analyticEventService,
                          @Mock DeltaFileCacheService deltaFileCacheService, @Mock RestDataSourceService restDataSourceService,
                          @Mock TimedDataSourceService timedDataSourceService, @Mock OnErrorDataSourceService onErrorDataSourceService,
                          @Mock QueueManagementService queueManagementService, @Mock QueuedAnnotationRepo queuedAnnotationRepo,
                          @Mock Environment environment, @Mock IdentityService identityService,
                          @Mock ParameterResolver parameterResolver, @Mock LocalContentStorageService localContentStorageService) {
        this.timedDataSourceService = timedDataSourceService;
        this.transformFlowService = transformFlowService;
        this.dataSinkService = dataSinkService;
        this.onErrorDataSourceService = onErrorDataSourceService;
        this.stateMachine = stateMachine;
        this.deltaFileRepo = deltaFileRepo;
        this.deltaFileFlowRepo = deltaFileFlowRepo;
        this.coreEventQueue = coreEventQueue;
        this.contentStorageService = contentStorageService;
        this.resumePolicyService = resumePolicyService;
        this.deltaFileCacheService = deltaFileCacheService;
        this.queueManagementService = queueManagementService;
        this.queuedAnnotationRepo = queuedAnnotationRepo;
        this.restDataSourceService = restDataSourceService;
        this.metricService = metricService;
        this.utilService = new UtilService(flowDefinitionService);
        this.analyticEventService = analyticEventService;

        MockDeltaFiPropertiesService mockDeltaFiPropertiesService = new MockDeltaFiPropertiesService();
        deltaFilesService = new DeltaFilesService(testClock, transformFlowService, dataSinkService, mockDeltaFiPropertiesService,
                stateMachine, annotationRepo, deltaFileRepo, deltaFileFlowRepo, coreEventQueue, contentStorageService, resumePolicyService,
                metricService, analyticEventService, new DidMutexService(), deltaFileCacheService, restDataSourceService, timedDataSourceService,
                onErrorDataSourceService, queueManagementService, queuedAnnotationRepo, environment, new TestUUIDGenerator(), identityService,
                flowDefinitionService, parameterResolver, localContentStorageService);
    }

    @AfterEach
    void finalizeMetrics() {
        verifyNoMoreInteractions(metricService);
    }

    @Test
    void basicIngressRest() {
        RestDataSource dataSource = FlowBuilders.buildDataSource("theFlow");
        when(restDataSourceService.getActiveFlowByName(dataSource.getName())).thenReturn(dataSource);

        UUID did = UUID.randomUUID();
        List<Content> content = Collections.singletonList(new Content("name", "mediaType"));
        IngressEventItem ingressInputItem = new IngressEventItem(did, "filename", dataSource.getName(),
                Map.of(), content, Map.of());

        DeltaFile deltaFile = deltaFilesService.ingressRest(dataSource, ingressInputItem, OffsetDateTime.now(), OffsetDateTime.now());

        assertNotNull(deltaFile);
        DeltaFileFlow ingressFlow = deltaFile.firstFlow();
        assertEquals(dataSource.getName(), ingressFlow.getName());
        assertEquals(did, deltaFile.getDid());
        assertTrue(ingressFlow.lastCompleteAction().isPresent());
    }

    @Test
    void ingressRestWithMetaAndAnnotations() {
        RestDataSource dataSource = FlowBuilders.buildDataSource("theFlow");
        dataSource.setMetadata(Map.of(
                "metaKeyX", "planX",
                "metaKeyY", "planY",
                "metaZ", "planZ"));
        dataSource.setAnnotationConfig(new AnnotationConfig(
                Map.of("annotation1", "plan", "annotation2", "plan"),
                List.of("metaKey.*"), "meta"));

        when(restDataSourceService.getActiveFlowByName(dataSource.getName())).thenReturn(dataSource);

        UUID did = UUID.randomUUID();
        List<Content> content = Collections.singletonList(new Content("name", "mediaType"));
        IngressEventItem ingressInputItem = new IngressEventItem(did, "filename", dataSource.getName(),
                Map.of("metaA", "eventA", "metaKeyX", "eventX"),
                content,
                Map.of("annotation1", "event1", "annotation3", "event3"));

        DeltaFile deltaFile = deltaFilesService.ingressRest(dataSource, ingressInputItem, OffsetDateTime.now(), OffsetDateTime.now());

        assertNotNull(deltaFile);
        DeltaFileFlow ingressFlow = deltaFile.firstFlow();
        assertEquals(dataSource.getName(), ingressFlow.getName());
        assertEquals(did, deltaFile.getDid());
        assertTrue(ingressFlow.lastCompleteAction().isPresent());

        Map<String, String> actualAnnotations = Annotation.toMap(deltaFile.getAnnotations());
        assertEquals("event1", actualAnnotations.get("annotation1"));
        assertEquals("plan", actualAnnotations.get("annotation2"));
        assertEquals("event3", actualAnnotations.get("annotation3"));
        assertEquals("eventX", actualAnnotations.get("KeyX"));
        assertEquals("planY", actualAnnotations.get("KeyY"));
        assertEquals(5, actualAnnotations.size());

        Map<String, String> actualMetadata = deltaFile.firstFlow().getMetadata();
        assertEquals("eventA", actualMetadata.get("metaA"));
        assertEquals("eventX", actualMetadata.get("metaKeyX"));
        assertEquals("planY", actualMetadata.get("metaKeyY"));
        assertEquals("planZ", actualMetadata.get("metaZ"));
        assertEquals(4, actualMetadata.size());
    }

    @Test
    void dataSourceTestModeTrue() {
        RestDataSource dataSource = FlowBuilders.buildDataSource("theFlow");
        dataSource.setTestMode(true);
        when(restDataSourceService.getActiveFlowByName(dataSource.getName())).thenReturn(dataSource);

        UUID did = UUID.randomUUID();
        List<Content> content = Collections.singletonList(new Content("name", "mediaType"));
        IngressEventItem ingressInputItem = new IngressEventItem(did, "filename", dataSource.getName(),
                Map.of(), content, Map.of());

        DeltaFile deltaFile = deltaFilesService.ingressRest(dataSource, ingressInputItem, OffsetDateTime.now(), OffsetDateTime.now());

        assertNotNull(deltaFile);
        DeltaFileFlow ingressFlow = deltaFile.firstFlow();
        assertEquals(dataSource.getName(), ingressFlow.getName());
        assertEquals(did, deltaFile.getDid());
        assertTrue(ingressFlow.isTestMode());
        assertTrue(ingressFlow.lastCompleteAction().isPresent());
    }

    @Test
    void getReturnsNullOnMissingDid() {
        assertNull(deltaFilesService.getDeltaFile(UUID.randomUUID()));
    }

    @Test
    void getRawDeltaFile() throws JsonProcessingException {
        DeltaFileFlow ingressFlow = new DeltaFileFlow();
        Action action = Action.builder().metadata(Map.of()).build();
        ingressFlow.setActions(List.of(action));
        ingressFlow.setInput(new DeltaFileFlowInput());
        ingressFlow.setFlowDefinition(flowDefinitionService.getOrCreateFlow("flow", FlowType.TIMED_DATA_SOURCE));
        DeltaFile deltaFile = DeltaFile.builder()
                .did(DID)
                .created(OffsetDateTime.parse("2022-09-29T12:30:00+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .flows(Set.of(ingressFlow))
                .build();
        deltaFile.wireBackPointers();
        when(deltaFileRepo.findById(DID)).thenReturn(Optional.of(deltaFile));
        String json = deltaFilesService.getRawDeltaFile(DID, false);
        assertTrue(json.contains("\"did\":\"%s\"".formatted(DID)));
        assertTrue(json.contains("\"created\":\"2022-09-29T11:30:00.000Z\""));
        assertEquals(1, json.split("\n").length);
    }

    @Test
    void getRawDeltaFilePretty() throws JsonProcessingException {
        DeltaFileFlow ingressFlow = new DeltaFileFlow();
        Action action = Action.builder().metadata(Map.of()).build();
        ingressFlow.setActions(List.of(action));
        ingressFlow.setInput(new DeltaFileFlowInput());
        ingressFlow.setFlowDefinition(flowDefinitionService.getOrCreateFlow("flow", FlowType.TIMED_DATA_SOURCE));
        DeltaFile deltaFile = DeltaFile.builder()
                .did(DID)
                .flows(Set.of(ingressFlow))
                .build();
        deltaFile.wireBackPointers();
        when(deltaFileRepo.findById(DID)).thenReturn(Optional.of(deltaFile));
        String json = deltaFilesService.getRawDeltaFile(DID, true);
        assertTrue(json.contains("  \"did\" : \"%s\",\n".formatted(DID)));
        assertNotEquals(1, json.split("\n").length);
    }

    @Test
    void getRawReturnsNullOnMissingDid() throws JsonProcessingException {
        assertNull(deltaFilesService.getRawDeltaFile(UUID.randomUUID(), true));
        assertNull(deltaFilesService.getRawDeltaFile(UUID.randomUUID(), false));
    }

    @Test
    void testSourceMetadataUnion() {
        DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), List.of(), Map.of("k1", "1a", "k2", "val2"));
        DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), List.of(), Map.of("k1", "1b", "k3", "val3"));

        // Map.of disallows null keys or values, so do it the hard way
        DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), List.of(), new HashMap<>());
        DeltaFileFlow ingressFlow = deltaFile3.firstFlow();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("k2", "val2");
        metadata.put("k3", null);
        metadata.put("k4", "val4");
        Action action = Action.builder().metadata(metadata).build();
        ingressFlow.getActions().add(action);
        deltaFile3.wireBackPointers();

        List<UUID> dids = List.of(deltaFile1.getDid(), deltaFile2.getDid(), deltaFile3.getDid(), UUID.randomUUID());
        List<DeltaFileFlow> deltaFileFlows = List.of(deltaFile1.firstFlow(), deltaFile2.firstFlow(), deltaFile3.firstFlow());
        when(deltaFileFlowRepo.findAllByDeltaFileIdsAndFlowZero(dids)).thenReturn(deltaFileFlows);

        List<UniqueKeyValues> uniqueMetadata = deltaFilesService.sourceMetadataUnion(dids);
        assertEquals(4, uniqueMetadata.size());

        boolean foundKey1 = false;
        boolean foundKey2 = false;
        boolean foundKey3 = false;
        boolean foundKey4 = false;

        for (UniqueKeyValues u : uniqueMetadata) {
            switch (u.getKey()) {
                case "k1" -> {
                    assertEquals(2, u.getValues().size());
                    assertTrue(u.getValues().containsAll(List.of("1a", "1b")));
                    foundKey1 = true;
                }
                case "k2" -> {
                    assertEquals(1, u.getValues().size());
                    assertTrue(u.getValues().contains("val2"));
                    foundKey2 = true;
                }
                case "k3" -> {
                    assertEquals(2, u.getValues().size());
                    List<String> listWithNull = new ArrayList<>();
                    listWithNull.add("val3");
                    listWithNull.add(null);
                    assertTrue(u.getValues().containsAll(listWithNull));
                    foundKey3 = true;
                }
                case "k4" -> {
                    assertEquals(1, u.getValues().size());
                    assertTrue(u.getValues().contains("val4"));
                    foundKey4 = true;
                }
            }
        }

        assertTrue(foundKey1);
        assertTrue(foundKey2);
        assertTrue(foundKey3);
        assertTrue(foundKey4);
    }

    @Test
    void testDelete() {
        UUID did1 = UUID.randomUUID();
        Content content1 = new Content("name", "mediaType", new Segment(UUID.randomUUID(), did1));
        DeltaFileDeleteDTO deltaFile1 = new DeltaFileDeleteDTO(did1, null, 0, List.of(content1.getSegments().getFirst().getUuid()));
        UUID did2 = UUID.randomUUID();
        Content content2 = new Content("name", "mediaType", new Segment(UUID.randomUUID(), did2));
        DeltaFileDeleteDTO deltaFile2 = new DeltaFileDeleteDTO(did2, null, 0, List.of(content2.getSegments().getFirst().getUuid()));
        when(deltaFileRepo.findForTimedDelete(any(), any(), anyLong(), any(), anyBoolean(), anyBoolean(), anyInt(), anyBoolean(), anyBoolean())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.timedDelete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", false, 1000, false);

        verify(contentStorageService).deleteAllByObjectName(stringListCaptor.capture());
        assertEquals(List.of(content1.getSegments().getFirst().objectName(), content2.getSegments().getFirst().objectName()), stringListCaptor.getValue());
        verify(deltaFileRepo).setContentDeletedByDidIn(uuidListCaptor.capture(), any(), eq("policy"));
        assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), uuidListCaptor.getValue());
        verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_FILES, 2).addTag("policy", "policy"));
        verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_BYTES, 0).addTag("policy", "policy"));
    }

    @Test
    void testDeleteMetadata() {
        UUID did1 = UUID.randomUUID();
        Content content1 = new Content("name", "mediaType", new Segment(UUID.randomUUID(), did1));
        DeltaFileDeleteDTO deltaFile1 = new DeltaFileDeleteDTO(did1, null, 0, List.of(content1.getSegments().getFirst().getUuid()));
        UUID did2 = UUID.randomUUID();
        Content content2 = new Content("name", "mediaType", new Segment(UUID.randomUUID(), did2));
        DeltaFileDeleteDTO deltaFile2 = new DeltaFileDeleteDTO(did2, null, 0, List.of(content2.getSegments().getFirst().getUuid()));
        when(deltaFileRepo.findForTimedDelete(any(), any(), anyLong(), any(), anyBoolean(), anyBoolean(), anyInt(), anyBoolean(), anyBoolean())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.timedDelete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", true, 1000, false);

        verify(contentStorageService).deleteAllByObjectName(stringListCaptor.capture());
        assertEquals(List.of(content1.getSegments().getFirst().objectName(), content2.getSegments().getFirst().objectName()), stringListCaptor.getValue());
        verify(deltaFileRepo, never()).saveAll(any());
        verify(deltaFileRepo).batchedBulkDeleteByDidIn(uuidListCaptor.capture());
        assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), uuidListCaptor.getValue());
        verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_FILES, 2).addTag("policy", "policy"));
        verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_BYTES, 0).addTag("policy", "policy"));
    }

    @Test
    void testAnnotationDeltaFile() {
        DeltaFile deltaFile = utilService.buildDeltaFile(UUID.randomUUID());
        deltaFile.addAnnotations(Map.of("key", "one"));

        when(deltaFileCacheService.isCached(deltaFile.getDid())).thenReturn(true);
        when(deltaFileCacheService.get(deltaFile.getDid())).thenReturn(deltaFile);
        when(deltaFileRepo.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());

        deltaFilesService.addAnnotations(deltaFile.getDid(), Map.of("sys-ack", "true"), false);
        verify(deltaFileCacheService).save(deltaFileCaptor.capture());

        DeltaFile after = deltaFileCaptor.getValue();
        Assertions.assertThat(after.annotationMap()).hasSize(2).containsEntry("key", "one").containsEntry("sys-ack", "true");
    }

    @Test
    void testAnnotationDeltaFile_badDid() {
        Map<String, String> metadata = Map.of("sys-ack", "true");
        UUID did = UUID.randomUUID();
        Assertions.assertThatThrownBy(() -> deltaFilesService.addAnnotations(did, metadata, true))
                .isInstanceOf(DgsEntityNotFoundException.class)
                .hasMessage("DeltaFile %s not found.".formatted(did));
    }

    @Test
    void testAddAnnotationOverwrites() {
        DeltaFile deltaFile = utilService.buildDeltaFile(UUID.randomUUID());
        deltaFile.addAnnotations(Map.of("key", "one"));

        when(deltaFileCacheService.isCached(deltaFile.getDid())).thenReturn(true);
        when(deltaFileCacheService.get(deltaFile.getDid())).thenReturn(deltaFile);

        deltaFilesService.addAnnotations(deltaFile.getDid(), Map.of("key", "changed"), false);
        Assertions.assertThat(deltaFile.annotationMap()).hasSize(1).containsEntry("key", "one");

        deltaFilesService.addAnnotations(deltaFile.getDid(), Map.of("key", "changed", "newKey", "value"), false);
        Assertions.assertThat(deltaFile.annotationMap()).hasSize(2).containsEntry("key", "one").containsEntry("newKey", "value");

        deltaFilesService.addAnnotations(deltaFile.getDid(), Map.of("key", "changed", "newKey", "value"), true);
        Assertions.assertThat(deltaFile.annotationMap()).hasSize(2).containsEntry("key", "changed").containsEntry("newKey", "value");
    }

    @Test
    void testQueueAnnotation_whenDeltaFileNotReady() {
        UUID did = UUID.randomUUID();
        when(deltaFileRepo.existsById(did)).thenReturn(true);
        when(deltaFileCacheService.isCached(did)).thenReturn(false);
        when(deltaFileRepo.findByDidAndStageIn(eq(did), anyList())).thenReturn(Optional.empty());

        deltaFilesService.addAnnotations(did, Map.of("queued", "true"), false);

        verify(queuedAnnotationRepo).save(queuedAnnotationCaptor.capture());
        verify(deltaFileCacheService, never()).save(any());
        verify(deltaFileRepo, never()).save(any());

        QueuedAnnotation after = queuedAnnotationCaptor.getValue();
        Assertions.assertThat(after.getAnnotations()).containsEntry("queued", "true");
    }

    @Test
    void testProcessQueuedAnnotations() {
        DeltaFile deltaFile = utilService.buildDeltaFile(UUID.randomUUID());

        QueuedAnnotation queuedAnnotation = new QueuedAnnotation(deltaFile.getDid(), Map.of("queued", "true"), false);
        when(queuedAnnotationRepo.findAllByOrderByTimeAsc()).thenReturn(List.of(queuedAnnotation));
        when(deltaFileCacheService.isCached(deltaFile.getDid())).thenReturn(true);
        when(deltaFileCacheService.get(deltaFile.getDid())).thenReturn(deltaFile);

        deltaFilesService.processQueuedAnnotations();
        verify(queuedAnnotationRepo).deleteById(queuedAnnotation.getId());
        Assertions.assertThat(deltaFile.annotationMap()).containsEntry("queued", "true");
    }

    @Test
    void testDeleteContentAndMetadata() {
        Content content = new Content();
        UUID did = UUID.randomUUID();
        deltaFilesService.deleteContentAndMetadata(did, content);

        verify(deltaFileRepo).deleteById(did);
        verify(contentStorageService).delete(content);
    }

    @Test
    void testDeleteContentAndMetadata_dbFail() {
        Content content = new Content();
        UUID did = UUID.randomUUID();
        doThrow(new RuntimeException("db fail")).when(deltaFileRepo).deleteById(did);
        deltaFilesService.deleteContentAndMetadata(did, content);

        // make sure content cleanup happens after a db failure
        verify(contentStorageService).delete(content);
    }

    @Test
    void testEgress_addPendingAnnotations() {
        // TODO: this doesn't make a lot of sense, we should make sure annotations are added when the dataSink is added in the state machine
        DataSink dataSink = new DataSink();
        dataSink.setExpectedAnnotations(Set.of("a", "b"));
        when(dataSinkService.hasFlow("dataSource")).thenReturn(true);
        when(dataSinkService.getActiveFlowByName("dataSource")).thenReturn(dataSink);

        DeltaFile deltaFile = utilService.buildDeltaFile(UUID.randomUUID());
        DeltaFileFlow flow = DeltaFileFlow.builder().flowDefinition(flowDefinitionService.getOrCreateFlow("dataSource", FlowType.DATA_SINK)).build();
        flow.setPendingAnnotations(Set.of("a", "b"));
        Action action = flow.queueAction("egress", null, ActionType.EGRESS, false, OffsetDateTime.now(testClock));
        deltaFile.getFlows().add(flow);
        deltaFile.wireBackPointers();
        deltaFilesService.egress(deltaFile, flow, action, OffsetDateTime.now(testClock), OffsetDateTime.now(testClock));

        Assertions.assertThat(deltaFile.pendingAnnotationFlows()).hasSize(1);
        Assertions.assertThat(deltaFile.pendingAnnotationFlows().getFirst().getName()).isEqualTo("dataSource");

        Map<String, String> flowTags = Map.of(
                "dataSource", "myFlow",
                "dataSink", "dataSource");

        verify(metricService).increment(new Metric(DeltaFiConstants.FILES_TO_SINK, 1).addTags(flowTags));
        verify(metricService).increment(new Metric(DeltaFiConstants.BYTES_TO_SINK, 0).addTags(flowTags));
    }

    @Test
    void testEgress_addPendingAnnotationsIgnoreNotRunningException() {
        // TODO: this doesn't make a lot of sense, we should make sure annotations are added when the dataSink is added in the state machine
        when(dataSinkService.hasFlow("dataSource")).thenReturn(true);
        when(dataSinkService.getActiveFlowByName("dataSource")).thenThrow(new DgsEntityNotFoundException("not running"));

        DeltaFile deltaFile = utilService.buildDeltaFile(UUID.randomUUID());
        DeltaFileFlow flow = deltaFile.firstFlow();
        Action action = flow.queueAction("egress", null, ActionType.EGRESS, false, OffsetDateTime.now(testClock));
        deltaFilesService.egress(deltaFile, flow, action, OffsetDateTime.now(testClock), OffsetDateTime.now(testClock));

        Assertions.assertThat(deltaFile.pendingAnnotationFlows()).isEmpty();

        Map<String, String> flowTags = Map.of(
                "dataSource", "myFlow",
                "dataSink", "myFlow");

        verify(metricService).increment(new Metric(DeltaFiConstants.FILES_TO_SINK, 1).addTags(flowTags));
        verify(metricService).increment(new Metric(DeltaFiConstants.BYTES_TO_SINK, 0).addTags(flowTags));
    }

    @Test
    void testGetPendingAnnotations() {
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setDid(UUID.randomUUID());
        deltaFile.setModified(OffsetDateTime.now());
        deltaFile.setFlows(Stream.of("a", "b", "c").map(this::deltaFileFlow).collect(Collectors.toSet()));
        when(deltaFileRepo.findById(deltaFile.getDid())).thenReturn(Optional.of(deltaFile));
        Assertions.assertThat(deltaFilesService.getPendingAnnotations(deltaFile.getDid())).hasSize(3).contains("a", "b", "c");

        when(deltaFileCacheService.isCached(deltaFile.getDid())).thenReturn(true);
        when(deltaFileCacheService.get(deltaFile.getDid())).thenReturn(deltaFile);
        deltaFilesService.addAnnotations(deltaFile.getDid(), Map.of("b", "2"), false);
        Assertions.assertThat(deltaFilesService.getPendingAnnotations(deltaFile.getDid())).hasSize(2).contains("a", "c");
    }

    private DeltaFileFlow deltaFileFlow(String name) {
        DeltaFileFlow flow = new DeltaFileFlow();
        flow.setFlowDefinition(flowDefinitionService.getOrCreateFlow(name, FlowType.DATA_SINK));
        flow.setPendingAnnotations(Set.of(name));
        flow.setActions(List.of(Action.builder().state(ActionState.COMPLETE).build()));
        return flow;
    }

    @Test
    void testGetPendingAnnotations_nullPendingList() {
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setDid(UUID.randomUUID());
        DeltaFileFlow deltaFileFlow = new DeltaFileFlow();
        deltaFile.setFlows(Set.of(deltaFileFlow));

        when(deltaFileRepo.findById(deltaFile.getDid())).thenReturn(Optional.of(deltaFile));
        Assertions.assertThat(deltaFilesService.getPendingAnnotations(deltaFile.getDid())).isEmpty();
    }

    @Test
    void testProcessActionEventsExceptionHandling() throws JsonProcessingException {
        when(coreEventQueue.takeResult(anyString()))
                .thenThrow(JsonProcessingException.class);
        assertFalse(deltaFilesService.processActionEvents("test"));
    }

    @Test
    void testErrorMetadataUnion() {
        DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), List.of());
        DeltaFileFlow deltaFileFlow1 = deltaFile1.firstFlow();
        deltaFileFlow1.setFlowDefinition(flowDefinitionService.getOrCreateFlow("flow1", deltaFileFlow1.getType()));
        deltaFileFlow1.firstAction().setMetadata(Map.of("a", "1", "b", "2"));
        Action error1 = deltaFileFlow1.queueAction("TransformAction1", null, ActionType.TRANSFORM, false, OffsetDateTime.now());
        error1.error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");
        deltaFileFlow1.setState(DeltaFileFlowState.ERROR);
        deltaFile1.wireBackPointers();

        DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), List.of());
        DeltaFileFlow deltaFileFlow2 = deltaFile2.firstFlow();
        deltaFileFlow2.setFlowDefinition(flowDefinitionService.getOrCreateFlow("flow1", deltaFileFlow2.getType()));
        deltaFileFlow2.firstAction().setMetadata(Map.of("a", "somethingElse", "c", "3"));
        Action error2 = deltaFileFlow2.queueAction("TransformAction1", null, ActionType.TRANSFORM, false, OffsetDateTime.now());
        error2.error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");
        deltaFileFlow2.setState(DeltaFileFlowState.ERROR);
        deltaFile2.wireBackPointers();

        DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), List.of());
        DeltaFileFlow deltaFileFlow3 = deltaFile3.firstFlow();
        deltaFileFlow3.setFlowDefinition(flowDefinitionService.getOrCreateFlow("flow2", deltaFileFlow3.getType()));
        deltaFileFlow3.firstAction().setMetadata(Map.of("d", "4"));
        Action error3 = deltaFileFlow3.queueAction("TransformAction2", null, ActionType.TRANSFORM, false, OffsetDateTime.now());
        error3.error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");
        deltaFileFlow3.setState(DeltaFileFlowState.ERROR);
        deltaFile3.wireBackPointers();

        DeltaFile deltaFile4 = utilService.buildDeltaFile(UUID.randomUUID(), List.of());
        DeltaFileFlow deltaFileFlow4 = deltaFile4.firstFlow();
        deltaFileFlow4.setFlowDefinition(flowDefinitionService.getOrCreateFlow("flow3", deltaFileFlow4.getType()));
        deltaFileFlow4.firstAction().setMetadata(Map.of("e", "5"));
        deltaFileFlow4.queueAction("TransformAction3", null, ActionType.TRANSFORM, false, OffsetDateTime.now());
        deltaFile4.wireBackPointers();

        List<DeltaFile> deltaFiles = List.of(deltaFile1, deltaFile2, deltaFile3, deltaFile4);
        when(deltaFileRepo.findByIdsIn(List.of(deltaFile1.getDid(), deltaFile2.getDid(), deltaFile3.getDid(), deltaFile4.getDid()))).thenReturn(deltaFiles);

        List<PerActionUniqueKeyValues> actionVals = deltaFilesService.errorMetadataUnion(List.of(deltaFile1.getDid(), deltaFile2.getDid(), deltaFile3.getDid(), deltaFile4.getDid()));

        assertEquals(2, actionVals.size());
        actionVals.sort(Comparator.comparing(PerActionUniqueKeyValues::getAction));
        assertEquals("flow1", actionVals.getFirst().getFlow());
        assertEquals("TransformAction1", actionVals.getFirst().getAction());
        assertEquals(3, actionVals.getFirst().getKeyVals().size());
        assertEquals("a", actionVals.getFirst().getKeyVals().getFirst().getKey());
        assertEquals(List.of("1", "somethingElse"), actionVals.getFirst().getKeyVals().getFirst().getValues());
        assertEquals("b", actionVals.getFirst().getKeyVals().get(1).getKey());
        assertEquals(List.of("2"), actionVals.getFirst().getKeyVals().get(1).getValues());
        assertEquals("c", actionVals.getFirst().getKeyVals().get(2).getKey());
        assertEquals(List.of("3"), actionVals.getFirst().getKeyVals().get(2).getValues());
        assertEquals("TransformAction2", actionVals.get(1).getAction());
        assertEquals("flow2", actionVals.get(1).getFlow());
        assertEquals(1, actionVals.get(1).getKeyVals().size());
        assertEquals("d", actionVals.get(1).getKeyVals().getFirst().getKey());
        assertEquals(List.of("4"), actionVals.get(1).getKeyVals().getFirst().getValues());
    }

    @Test
    void requeuesJoinedAction() {
        DeltaFile aggregate = utilService.buildDeltaFile(UUID.randomUUID());
        aggregate.setJoinId(aggregate.getDid());
        DeltaFile parent1 = utilService.buildDeltaFile(UUID.randomUUID());
        parent1.firstFlow().setJoinId(aggregate.getDid());
        DeltaFile parent2 = utilService.buildDeltaFile(UUID.randomUUID());
        parent2.firstFlow().setJoinId(aggregate.getDid());
        aggregate.setParentDids(List.of(parent1.getDid(), parent2.getDid()));

        DeltaFileFlow flow = aggregate.firstFlow();
        flow.setState(DeltaFileFlowState.IN_FLIGHT);
        flow.queueAction("join-transform", null, ActionType.TRANSFORM, false, OffsetDateTime.now().minusYears(1));

        when(coreEventQueue.getLongRunningTasks()).thenReturn(Collections.emptyList());
        when(queueManagementService.coldQueueActions()).thenReturn(Collections.emptySet());
        when(deltaFileRepo.findForRequeue(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(aggregate));
        when(deltaFileRepo.findAllById(List.of(parent1.getDid(), parent2.getDid())))
                .thenReturn(List.of(parent1, parent2));

        ActionConfiguration ActionConfiguration =
                new ActionConfiguration("join-transform", ActionType.TRANSFORM, "org.deltafi.SomeJoiningTransformAction");
        ActionConfiguration.setJoin(new JoinConfiguration(Duration.parse("PT1H"), null, 3, null));
        when(timedDataSourceService.findRunningActionConfig("myFlow", "join-transform"))
                .thenReturn(ActionConfiguration);

        deltaFilesService.requeue();

        verify(deltaFileRepo).saveAll(List.of(aggregate));
        verify(coreEventQueue).putActions(actionInputListCaptor.capture(), anyBoolean());
        List<WrappedActionInput> enqueuedActions = actionInputListCaptor.getValue();
        assertEquals(1, enqueuedActions.size());
        assertEquals(List.of(parent1.getDid(), parent2.getDid()), enqueuedActions.getFirst().getActionContext().getJoinedDids());
        assertEquals(2, enqueuedActions.getFirst().getDeltaFileMessages().size());
    }

    @Test
    void applyResumePolicies() {
        deltaFilesService.applyResumePolicies(List.of("name1"));
        verify(resumePolicyService, times(1)).refreshCache();
    }

    @Test
    void testReplayChild() {
        UUID uuid = UUID.randomUUID();
        DeltaFile deltaFile = UtilService.buildDeltaFile(uuid, "myFlow");

        DeltaFileFlow flow = deltaFile.addFlow(FlowDefinition.builder().name("dataSource").type(FlowType.TRANSFORM).build(), new DeltaFileFlow(), OffsetDateTime.now(testClock));

        flow.addAction("parentAction1", null, ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        flow.addAction("parentAction2", null, ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        Action starter = flow.addAction("splitAction", null, ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);
        flow.addAction("childAction", null, ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        flow.addAction("childActionOldName", null, ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock)); // renamed action after the split should not cause an error

        TransformFlow flowConfig = new TransformFlow();
        flowConfig.setName("dataSource");
        flowConfig.setTransformActions(mockActions("parentAction1", "parentAction2", "splitAction", "childAction", "childActionNewName"));

        List<UUID> dids = List.of(uuid);
        when(deltaFileRepo.findAllById(dids)).thenReturn(List.of(deltaFile));
        when(transformFlowService.getFlowOrThrow("dataSource")).thenReturn(flowConfig);

        List<RetryResult> results = deltaFilesService.replay(dids, List.of(), List.of());

        assertEquals(1, results.size());
        RetryResult result = results.getFirst();
        assertTrue(result.getSuccess());

        verify(stateMachine).advance(stateMachineInputCaptor.capture());

        List<StateMachineInput> stateMachines = stateMachineInputCaptor.getValue();
        assertEquals(1, stateMachines.size());
        DeltaFile child = stateMachines.getFirst().deltaFile();
        String nextAction = child.firstFlow().getNextPendingAction();
        DeltaFileFlow childFirstFlow = child.firstFlow();
        assertEquals(2, childFirstFlow.getPendingActions().size());
        assertEquals("childAction", nextAction);
        List<Action> childActions = childFirstFlow.getActions();
        assertEquals(4, childActions.size());
        List<String> expectedActions = List.of("parentAction1", "parentAction2", "splitAction", "Replay");
        for (int i = 0; i < expectedActions.size(); i++) {
            assertEquals(expectedActions.get(i), childActions.get(i).getName());
        }
    }

    @Test
    void testReplayChildRemovedParentAction() {
        UUID uuid = UUID.randomUUID();
        DeltaFile deltaFile = UtilService.buildDeltaFile(uuid, "myFlow");

        DeltaFileFlow flow = deltaFile.addFlow(FlowDefinition.builder().name("dataSource").type(FlowType.TRANSFORM).build(), new DeltaFileFlow(), OffsetDateTime.now(testClock));

        flow.addAction("parentAction1", null, ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        flow.addAction("removedParentAction", null, ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        Action starter = flow.addAction("splitAction", null, ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);

        TransformFlow flowConfig = new TransformFlow();
        flowConfig.setName("dataSource");
        flowConfig.setTransformActions(mockActions("parentAction1", "splitAction", "childAction", "childAction2"));

        List<UUID> dids = List.of(uuid);
        when(deltaFileRepo.findAllById(dids)).thenReturn(List.of(deltaFile));
        when(transformFlowService.getFlowOrThrow("dataSource")).thenReturn(flowConfig);

        List<RetryResult> results = deltaFilesService.replay(dids, List.of(), List.of());

        assertEquals(1, results.size());
        RetryResult result = results.getFirst();
        assertFalse(result.getSuccess());
        assertEquals("The actions inherited from the parent DeltaFile for dataSource dataSource do not match the latest dataSource", result.getError());
    }

    @Test
    void testReplayChildAddedParentAction() {
        UUID uuid = UUID.randomUUID();
        DeltaFile deltaFile = UtilService.buildDeltaFile(uuid, "myFlow");

        DeltaFileFlow flow = deltaFile.addFlow(FlowDefinition.builder().name("dataSource").type(FlowType.TRANSFORM).build(), new DeltaFileFlow(), OffsetDateTime.now(testClock));

        flow.addAction("parentAction1", null, ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        flow.addAction("parentAction2", null, ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        Action starter = flow.addAction("splitAction", null, ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);
        flow.addAction("childAction", null, ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);

        TransformFlow flowConfig = new TransformFlow();
        flowConfig.setName("dataSource");
        flowConfig.setTransformActions(mockActions("parentAction1", "parentAction2", "extraParentAction", "splitAction", "childAction", "childAction2"));

        List<UUID> dids = List.of(uuid);
        when(deltaFileRepo.findAllById(dids)).thenReturn(List.of(deltaFile));
        when(transformFlowService.getFlowOrThrow("dataSource")).thenReturn(flowConfig);

        List<RetryResult> results = deltaFilesService.replay(dids, List.of(), List.of());

        assertEquals(1, results.size());
        RetryResult result = results.getFirst();
        assertFalse(result.getSuccess());
        assertEquals("The actions inherited from the parent DeltaFile for dataSource dataSource do not match the latest dataSource", result.getError());
    }

    @Test
    void testReplayChildRenamedParentAction() {
        UUID uuid = UUID.randomUUID();
        DeltaFile deltaFile = UtilService.buildDeltaFile(uuid, "myFlow");

        DeltaFileFlow flow = deltaFile.addFlow(FlowDefinition.builder().name("dataSource").type(FlowType.TRANSFORM).build(), new DeltaFileFlow(), OffsetDateTime.now(testClock));

        flow.addAction("parentAction1", null, ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        flow.addAction("parentActionOldName", null, ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        Action starter = flow.addAction("splitAction", null, ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);

        TransformFlow flowConfig = new TransformFlow();
        flowConfig.setName("dataSource");
        flowConfig.setTransformActions(mockActions("parentAction1", "parentActionNewName", "splitAction", "childAction", "childAction2"));

        List<UUID> dids = List.of(uuid);
        when(deltaFileRepo.findAllById(dids)).thenReturn(List.of(deltaFile));
        when(transformFlowService.getFlowOrThrow("dataSource")).thenReturn(flowConfig);

        List<RetryResult> results = deltaFilesService.replay(dids, List.of(), List.of());

        assertEquals(1, results.size());
        RetryResult result = results.getFirst();
        assertFalse(result.getSuccess());
        assertEquals("The actions inherited from the parent DeltaFile for dataSource dataSource do not match the latest dataSource", result.getError());
    }

    @Test
    void testReplayChildSplitActionRenamed() {
        UUID uuid = UUID.randomUUID();
        DeltaFile deltaFile = UtilService.buildDeltaFile(uuid, "myFlow");

        DeltaFileFlow flow = deltaFile.addFlow(FlowDefinition.builder().name("dataSource").type(FlowType.TRANSFORM).build(), new DeltaFileFlow(), OffsetDateTime.now(testClock));

        flow.addAction("parentAction1", null, ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        flow.addAction("parentAction2", null, ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        Action starter = flow.addAction("splitActionOld", null, ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);
        flow.addAction("childAction", null, ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        flow.addAction("childActionOldName", null, ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock)); // renamed action after the split should not cause an error

        TransformFlow flowConfig = new TransformFlow();
        flowConfig.setName("dataSource");
        flowConfig.setTransformActions(mockActions("parentAction1", "parentAction2", "splitActionNew", "childAction", "childActionNewName"));

        List<UUID> dids = List.of(uuid);
        when(deltaFileRepo.findAllById(dids)).thenReturn(List.of(deltaFile));
        when(transformFlowService.getFlowOrThrow("dataSource")).thenReturn(flowConfig);

        List<RetryResult> results = deltaFilesService.replay(dids, List.of(), List.of());

        assertEquals(1, results.size());
        RetryResult result = results.getFirst();
        assertFalse(result.getSuccess());
        assertEquals("The dataSource dataSource no longer contains an action named splitActionOld where the replay would be begin", result.getError());
    }

    @Test
    void testHandleMissingFlow() {
        UUID did = UUID.randomUUID();
        DeltaFile deltaFile = utilService.buildDeltaFile(did);
        DeltaFileFlow flow = deltaFile.firstFlow();

        deltaFilesService.handleMissingFlow(deltaFile, flow, new MissingFlowException("flowName", FlowType.TRANSFORM, FlowState.INVALID));

        assertEquals(DeltaFileStage.ERROR, deltaFile.getStage());
        Action errorAction = flow.lastAction();
        assertEquals("MissingRunningFlow", errorAction.getName());
        assertEquals(ActionType.UNKNOWN, errorAction.getType());
        assertEquals(ActionState.ERROR, errorAction.getState());
        verify(metricService).increment(new Metric(DeltaFiConstants.FILES_ERRORED, 1).addTags(Map.of("action", "unknown", "source", "MissingRunningFlow", "dataSource", "myFlow")));
    }

    @Test
    void testProcessErrorEvent() {
        UUID did = UUID.randomUUID();
        DeltaFile deltaFile = utilService.buildDeltaFile(did);
        DeltaFileFlow flow = deltaFile.firstFlow();
        Action action = flow.firstAction();

        ActionEvent event = ActionEvent.builder()
                .did(did)
                .flowName(flow.getName())
                .actionName(action.getName())
                .type(ActionEventType.ERROR)
                .error(ErrorEvent.builder()
                        .cause("Test error")
                        .context("Error context")
                        .annotations(Map.of("error", "true"))
                        .build())
                .start(OffsetDateTime.now(testClock))
                .stop(OffsetDateTime.now(testClock))
                .build();

        Optional<ResumePolicyService.ResumeDetails> resumeDetails =
                Optional.of(new ResumePolicyService.ResumeDetails("policy1", 60));
        when(resumePolicyService.getAutoResumeDelay(eq(deltaFile), eq(action), eq("Test error")))
                .thenReturn(resumeDetails);

        deltaFilesService.processErrorEvent(deltaFile, flow, action, event);

        assertEquals(DeltaFileStage.ERROR, deltaFile.getStage());
        assertEquals(ActionState.ERROR, action.getState());
        assertNotNull(action.getNextAutoResume());
        assertEquals("policy1", action.getNextAutoResumeReason());

        verify(metricService).increment(new Metric(DeltaFiConstants.FILES_ERRORED, 1)
                .addTag("source", action.getName())
                .addTag("action", "error")
                .addTag("dataSource", deltaFile.getDataSource()));
    }

    private List<ActionConfiguration> mockActions(String... names) {
        return Stream.of(names).map(this::mockAction).toList();
    }

    private ActionConfiguration mockAction(String name) {
        return new ActionConfiguration(name, ActionType.TRANSFORM, "org.action." + name);
    }

    @Test
    void testCheckSeverityAndAddMessages() {
        DeltaFile deltaFile = utilService.buildDeltaFile(UUID.randomUUID(), "unusedDataSource", DeltaFileStage.COMPLETE,
                OffsetDateTime.now(), OffsetDateTime.now());

        deltaFilesService.checkSeverityAndAddMessages(deltaFile, List.of(
                LogMessage.createTrace("source", "nope1"),
                LogMessage.createInfo("source", "yes"),
                LogMessage.createTrace("source", "nope2"),
                LogMessage.createWarning("source", "yes")));

        assertEquals(2, deltaFile.getMessages().size());

        assertEquals("yes", deltaFile.getMessages().getFirst().getMessage());
        assertEquals(LogSeverity.INFO, deltaFile.getMessages().getFirst().getSeverity());
        assertEquals("source", deltaFile.getMessages().getFirst().getSource());

        assertEquals("yes", deltaFile.getMessages().getLast().getMessage());
        assertEquals(LogSeverity.WARNING, deltaFile.getMessages().getLast().getSeverity());
        assertEquals("source", deltaFile.getMessages().getLast().getSource());
    }

    @Test
    void userNotesDeltaFiles() {
        DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "unusedDataSource", DeltaFileStage.COMPLETE,
                OffsetDateTime.now(), OffsetDateTime.now());
        DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "unusedDataSource", DeltaFileStage.ERROR,
                OffsetDateTime.now(), OffsetDateTime.now());
        deltaFile2.addActionMessages(List.of(LogMessage.createError("source", "text")));
        DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID()); // IN_FLIGHT
        UUID nonExistentDid = UUID.randomUUID();

        when(deltaFileRepo.findById(eq(deltaFile1.getDid()))).thenReturn(Optional.of(deltaFile1));
        when(deltaFileRepo.findById(eq(deltaFile2.getDid()))).thenReturn(Optional.of(deltaFile2));
        when(deltaFileRepo.findById(eq(deltaFile3.getDid()))).thenReturn(Optional.of(deltaFile3));

        List<Result> results = deltaFilesService.userNote(List.of(deltaFile1.getDid(), deltaFile2.getDid(),
                deltaFile3.getDid(), nonExistentDid), "message", "userName");

        assertEquals(4, results.size());
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(1).isSuccess());
        assertFalse(results.get(2).isSuccess());
        assertFalse(results.get(3).isSuccess());

        assertTrue(deltaFile1.isUserNotes());
        assertEquals(1, deltaFile1.getMessages().size());
        assertEquals("message", deltaFile1.getMessages().getFirst().getMessage());
        assertEquals(LogSeverity.USER, deltaFile1.getMessages().getFirst().getSeverity());
        assertEquals("userName", deltaFile1.getMessages().getFirst().getSource());

        assertTrue(deltaFile2.isUserNotes());
        assertEquals(2, deltaFile2.getMessages().size());
        assertEquals("message", deltaFile2.getMessages().getLast().getMessage());
        assertEquals(LogSeverity.USER, deltaFile2.getMessages().getLast().getSeverity());
        assertEquals("userName", deltaFile2.getMessages().getLast().getSource());

        assertFalse(deltaFile3.isUserNotes());
        assertNull(deltaFile3.getMessages());
        verify(deltaFileRepo, times(2)).save(any());
        assertEquals(1, results.get(2).getErrors().size());
        assertEquals("DeltaFile with did " + deltaFile3.getDid() + " is still IN_FLIGHT",
                results.get(2).getErrors().getFirst());
        assertEquals("DeltaFile with did " + nonExistentDid + " doesn't exist",
                results.get(3).getErrors().getFirst());
    }

    @Test
    void pinsDeltaFiles() {
        DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "unusedDataSource", DeltaFileStage.COMPLETE,
                OffsetDateTime.now(), OffsetDateTime.now());
        DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "unusedDataSource", DeltaFileStage.COMPLETE,
                OffsetDateTime.now(), OffsetDateTime.now());
        DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID()); // IN_FLIGHT
        UUID nonExistentDid = UUID.randomUUID();

        when(deltaFileRepo.findById(eq(deltaFile1.getDid()))).thenReturn(Optional.of(deltaFile1));
        when(deltaFileRepo.findById(eq(deltaFile2.getDid()))).thenReturn(Optional.of(deltaFile2));
        when(deltaFileRepo.findById(eq(deltaFile3.getDid()))).thenReturn(Optional.of(deltaFile3));

        List<Result> results = deltaFilesService.pin(List.of(deltaFile1.getDid(), deltaFile2.getDid(),
                deltaFile3.getDid(), nonExistentDid));

        assertTrue(deltaFile1.isPinned());
        assertTrue(deltaFile2.isPinned());
        assertFalse(deltaFile3.isPinned());
        verify(deltaFileRepo, times(2)).save(any());
        assertEquals(4, results.size());
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(1).isSuccess());
        assertFalse(results.get(2).isSuccess());
        assertEquals(1, results.get(2).getInfo().size());
        assertEquals("DeltaFile with did " + deltaFile3.getDid() + " hasn't completed",
                results.get(2).getInfo().getFirst());
        assertEquals("DeltaFile with did " + nonExistentDid + " doesn't exist",
                results.get(3).getInfo().getFirst());
    }

    @Test
    void unpinsDeltaFiles() {
        DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "unusedDataSource", DeltaFileStage.COMPLETE,
                OffsetDateTime.now(), OffsetDateTime.now());
        deltaFile1.setPinned(true);
        DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "unusedDataSource", DeltaFileStage.COMPLETE,
                OffsetDateTime.now(), OffsetDateTime.now());
        deltaFile2.setPinned(true);

        when(deltaFileRepo.findById(eq(deltaFile1.getDid()))).thenReturn(Optional.of(deltaFile1));
        when(deltaFileRepo.findById(eq(deltaFile2.getDid()))).thenReturn(Optional.of(deltaFile2));

        deltaFilesService.unpin(List.of(deltaFile1.getDid(), deltaFile2.getDid()));

        assertFalse(deltaFile1.isPinned());
        assertFalse(deltaFile2.isPinned());
    }

    @Test
    void resumeMatching() {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        deltaFilesService.resume(filter, List.of());

        Mockito.verify(deltaFileRepo).deltaFiles(filter, 5000);
        assertThat(filter.getStage()).isEqualTo(DeltaFileStage.ERROR);
        assertThat(filter.getContentDeleted()).isFalse();
        assertThat(filter.getModifiedBefore()).isNotNull();
    }

    @Test
    void resumeMatching_skipNonResumableFilters() {
        deltaFilesService.resume(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.IN_FLIGHT).build(), List.of());
        deltaFilesService.resume(DeltaFilesFilter.newBuilder().contentDeleted(true).build(), List.of());

        Mockito.verifyNoInteractions(deltaFileRepo);
    }

    @Test
    void ensureModifiedBeforeNow_setMissing() {
        DeltaFilesFilter filter = new DeltaFilesFilter();

        deltaFilesService.ensureModifiedBeforeNow(filter);
        assertThat(filter.getModifiedBefore()).isEqualTo(OffsetDateTime.now(testClock));
    }

    @Test
    void ensureModifiedBeforeNow_leaveOlder() {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        OffsetDateTime yesterday = OffsetDateTime.now().minusDays(1);
        filter.setModifiedBefore(yesterday);
        deltaFilesService.ensureModifiedBeforeNow(filter);

        assertThat(filter.getModifiedBefore()).isEqualTo(yesterday);
    }

    @Test
    void ensureModifiedBeforeNow_replaceNewer() {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        OffsetDateTime later = OffsetDateTime.now(testClock).plusMinutes(1);
        filter.setModifiedBefore(later);

        deltaFilesService.ensureModifiedBeforeNow(filter);

        assertThat(filter.getModifiedBefore()).isEqualTo(OffsetDateTime.now(testClock));
    }

    @Test
    void acknowledgeMatching() {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        deltaFilesService.acknowledge(filter, "reason");

        Mockito.verify(deltaFileRepo).deltaFiles(filter, 5000);
        assertThat(filter.getStage()).isEqualTo(DeltaFileStage.ERROR);
        assertThat(filter.getErrorAcknowledged()).isFalse();
        assertThat(filter.getModifiedBefore()).isNotNull();
    }

    @Test
    void acknowledgeMatching_skipInvalidAcknowledgeFilters() {
        deltaFilesService.acknowledge(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.IN_FLIGHT).build(), "reason");
        Mockito.verifyNoInteractions(deltaFileRepo);
    }

    @Test
    void cancelMatching() {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        deltaFilesService.cancel(filter);

        Mockito.verify(deltaFileRepo).deltaFiles(filter, 5000);
        assertThat(filter.getStage()).isEqualTo(DeltaFileStage.IN_FLIGHT);
        assertThat(filter.getModifiedBefore()).isNotNull();
    }

    @Test
    void cancelMatching_skipNonCancelableFilters() {
        deltaFilesService.cancel(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.CANCELLED).build());
        Mockito.verifyNoInteractions(deltaFileRepo);
    }

    @Test
    void replayMatching() {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        deltaFilesService.replay(filter, null, null);

        Mockito.verify(deltaFileRepo).deltaFiles(filter, 5000);
        assertThat(filter.getReplayable()).isTrue();
        assertThat(filter.getModifiedBefore()).isNotNull();
    }

    @Test
    void replayMatching_skipNonReplayableFilters() {
        deltaFilesService.replay(DeltaFilesFilter.newBuilder().contentDeleted(true).build(), null, null);
        deltaFilesService.replay(DeltaFilesFilter.newBuilder().replayed(true).build(), null, null);
        deltaFilesService.replay(DeltaFilesFilter.newBuilder().replayable(false).build(), null, null);
        Mockito.verifyNoInteractions(deltaFileRepo);
    }

    @Test
    void setPinned() {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        deltaFilesService.setPinned(filter, true);

        Mockito.verify(deltaFileRepo).deltaFiles(filter, 5000);
        assertThat(filter.getStage()).isEqualTo(DeltaFileStage.COMPLETE);
        assertThat(filter.getModifiedBefore()).isNotNull();
        assertThat(filter.getPinned()).isFalse();
    }

    @Test
    void pinMatching_skipNonPinnableFilters() {
        deltaFilesService.setPinned(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.IN_FLIGHT).build(), true);
        deltaFilesService.setPinned(DeltaFilesFilter.newBuilder().pinned(true).build(), true);
        deltaFilesService.setPinned(DeltaFilesFilter.newBuilder().pinned(false).build(), false);
        Mockito.verifyNoInteractions(deltaFileRepo);
    }

    @Test
    void annotateMatching() {
        DeltaFile terminal = utilService.buildDeltaFile(UUID.randomUUID(), "terminal", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
        terminal.setTerminal(true);
        DeltaFile inFlight = utilService.buildDeltaFile(UUID.randomUUID());
        DeltaFile cachedInFlight = utilService.buildDeltaFile(UUID.randomUUID());
        DeltaFile flushedFromCache = utilService.buildDeltaFile(UUID.randomUUID());

        DeltaFilesFilter filter = new DeltaFilesFilter();
        Map<String, String> annotations = Map.of("k", "v");

        Mockito.when(deltaFileRepo.deltaFiles(filter, 5000)).thenReturn(List.of(terminal, cachedInFlight, inFlight, flushedFromCache));
        Mockito.when(deltaFileCacheService.isCached(cachedInFlight.getDid())).thenReturn(true);
        Mockito.when(deltaFileCacheService.get(cachedInFlight.getDid())).thenReturn(cachedInFlight);
        Mockito.when(deltaFileCacheService.isCached(flushedFromCache.getDid())).thenReturn(true);
        Mockito.when(deltaFileCacheService.get(flushedFromCache.getDid())).thenReturn(null);

        deltaFilesService.annotateMatching(filter, annotations, true);

        assertThat(filter.getModifiedBefore()).isNotNull();

        // terminal DeltaFile has save called immediately along with adding analytics
        Mockito.verify(deltaFileCacheService).save(terminal);
        Mockito.verify(analyticEventService).queueAnnotations(terminal.getDid(), annotations);

        // locally cached DeltaFile is processed immediately
        assertThat(cachedInFlight.getAnnotations()).anyMatch(annotation -> annotation.getKey().equals("k") && annotation.getValue().equals("v"));
        Mockito.verify(deltaFileCacheService).save(cachedInFlight);
        Mockito.verify(analyticEventService).queueAnnotations(cachedInFlight.getDid(), annotations);

        // in-flight and flushed DeltaFile have queued annotation added
        Mockito.verify(queuedAnnotationRepo).saveAll(queuedAnnotationListCaptor.capture());
        List<QueuedAnnotation> queuedAnnotations = queuedAnnotationListCaptor.getValue();

        assertThat(queuedAnnotations).hasSize(2)
                .anyMatch(queuedAnnotation -> didMatches(queuedAnnotation, inFlight.getDid()))
                .anyMatch(queuedAnnotation -> didMatches(queuedAnnotation, flushedFromCache.getDid()));
    }

    private boolean didMatches(QueuedAnnotation queuedAnnotation, UUID expectedDid) {
        return queuedAnnotation.getDid().equals(expectedDid);
    }

    @Test
    void testOnErrorDataSourceTriggering() {
        List<OnErrorDataSource> mockDataSources = List.of(new OnErrorDataSource());
        when(onErrorDataSourceService.getTriggeredDataSources(
                eq("testFlow"), eq(FlowType.TRANSFORM), eq("TestAction"), eq("com.example.TestAction"),
                eq("Test error message"), any(), any()))
                .thenReturn(mockDataSources);

        List<OnErrorDataSource> result = onErrorDataSourceService.getTriggeredDataSources(
                "testFlow", FlowType.TRANSFORM, "TestAction", "com.example.TestAction", "Test error message", 
                Map.of(), Map.of());

        assertThat(result).hasSize(1);
        verify(onErrorDataSourceService).getTriggeredDataSources(
                "testFlow", FlowType.TRANSFORM, "TestAction", "com.example.TestAction", "Test error message", 
                Map.of(), Map.of());
    }
}
