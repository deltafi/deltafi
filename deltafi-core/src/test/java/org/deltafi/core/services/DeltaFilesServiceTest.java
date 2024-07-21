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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.assertj.core.api.Assertions;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.test.uuid.TestUUIDGenerator;
import org.deltafi.common.types.*;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.join.ScheduledJoinService;
import org.deltafi.core.generated.types.RetryResult;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.repo.*;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.types.*;
import org.deltafi.core.util.FlowBuilders;
import org.deltafi.core.util.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeltaFilesServiceTest {
    private final TestClock testClock = new TestClock();
    private final MockDeltaFiPropertiesService mockDeltaFiPropertiesService = new MockDeltaFiPropertiesService();

    private final TransformFlowService transformFlowService;
    private final RestDataSourceService restDataSourceService;
    private final EgressFlowService egressFlowService;
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

    private static final UUID DID = UUID.randomUUID();

    @Captor
    ArgumentCaptor<List<Segment>> segmentCaptor;

    @Captor
    ArgumentCaptor<List<UUID>> uuidListCaptor;

    @Captor
    ArgumentCaptor<DeltaFile> deltaFileCaptor;

    @Captor
    ArgumentCaptor<List<WrappedActionInput>> actionInputListCaptor;

    @Captor
    ArgumentCaptor<List<StateMachineInput>> stateMachineInputCaptor;

    @Captor
    ArgumentCaptor<QueuedAnnotation> queuedAnnotationCaptor;

    DeltaFilesServiceTest(@Mock TransformFlowService transformFlowService,
                          @Mock EgressFlowService egressFlowService, @Mock StateMachine stateMachine,
                          @Mock AnnotationRepo annotationRepo, @Mock DeltaFileRepo deltaFileRepo,
                          @Mock ActionRepo actionRepo, @Mock DeltaFileFlowRepo deltaFileFlowRepo,
                          @Mock CoreEventQueue coreEventQueue, @Mock ContentStorageService contentStorageService,
                          @Mock ResumePolicyService resumePolicyService, @Mock MetricService metricService,
                          @Mock AnalyticEventService analyticEventService, @Mock CoreAuditLogger coreAuditLogger,
                          @Mock DeltaFileCacheService deltaFileCacheService, @Mock RestDataSourceService restDataSourceService,
                          @Mock TimedDataSourceService timedDataSourceService,
                          @Mock QueueManagementService queueManagementService, @Mock QueuedAnnotationRepo queuedAnnotationRepo,
                          @Mock Environment environment, @Mock ScheduledJoinService scheduledJoinService) {
        this.transformFlowService = transformFlowService;
        this.egressFlowService = egressFlowService;
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

        deltaFilesService = new DeltaFilesService(testClock, transformFlowService, egressFlowService, mockDeltaFiPropertiesService,
                stateMachine, annotationRepo, deltaFileRepo, deltaFileFlowRepo, actionRepo, coreEventQueue, contentStorageService, resumePolicyService,
                metricService, analyticEventService, coreAuditLogger, new DidMutexService(), deltaFileCacheService,
                timedDataSourceService, queueManagementService, queuedAnnotationRepo, environment, scheduledJoinService, new TestUUIDGenerator());
    }

    @Test
    void setsAndGets() {
        RestDataSource dataSource = FlowBuilders.buildDataSource("theFlow");
        when(restDataSourceService.getRunningFlowByName(dataSource.getName())).thenReturn(dataSource);

        UUID did = UUID.randomUUID();
        List<Content> content = Collections.singletonList(new Content("name", "mediaType"));
        IngressEventItem ingressInputItem = new IngressEventItem(did, "filename", dataSource.getName(),
                Map.of(), content);

        DeltaFile deltaFile = deltaFilesService.ingress(dataSource, ingressInputItem, OffsetDateTime.now(), OffsetDateTime.now());

        assertNotNull(deltaFile);
        DeltaFileFlow ingressFlow = deltaFile.getFlows().getFirst();
        assertEquals(dataSource.getName(), ingressFlow.getName());
        assertEquals(did, deltaFile.getDid());
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
        DeltaFile deltaFile = DeltaFile.builder()
                .did(DID)
                .created(OffsetDateTime.parse("2022-09-29T12:30:00+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .flows(List.of(ingressFlow))
                .build();
        when(deltaFileRepo.findById(DID)).thenReturn(Optional.ofNullable(deltaFile));
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
        DeltaFile deltaFile = DeltaFile.builder()
                .did(DID)
                .flows(List.of(ingressFlow))
                .build();
        when(deltaFileRepo.findById(DID)).thenReturn(Optional.ofNullable(deltaFile));
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
        DeltaFile deltaFile1 = Util.buildDeltaFile(UUID.randomUUID(), List.of(), Map.of("k1", "1a", "k2", "val2"));
        DeltaFile deltaFile2 = Util.buildDeltaFile(UUID.randomUUID(), List.of(), Map.of("k1", "1b", "k3", "val3"));

        // Map.of disallows null keys or values, so do it the hard way
        DeltaFile deltaFile3 = Util.buildDeltaFile(UUID.randomUUID(), List.of(), new HashMap<>());
        DeltaFileFlow ingressFlow = deltaFile3.getFlows().getFirst();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("k2", "val2");
        metadata.put("k3", null);
        metadata.put("k4", "val4");
        ingressFlow.getInput().setMetadata(metadata);
        Action action = Action.builder().metadata(metadata).build();
        ingressFlow.getActions().add(action);

        List<UUID> dids = List.of(deltaFile1.getDid(), deltaFile2.getDid(), deltaFile3.getDid(), UUID.randomUUID());
        List<DeltaFileFlow> deltaFileFlows = List.of(deltaFile1.getFlows().getFirst(), deltaFile2.getFlows().getFirst(), deltaFile3.getFlows().getFirst());
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
        DeltaFile deltaFile1 = Util.buildDeltaFile(did1, List.of(content1));
        UUID did2 = UUID.randomUUID();
        Content content2 = new Content("name", "mediaType", new Segment(UUID.randomUUID(), did2));
        DeltaFile deltaFile2 = Util.buildDeltaFile(did2, List.of(content2));
        when(deltaFileRepo.findForTimedDelete(any(), any(), anyLong(), any(), anyBoolean(), anyInt())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.timedDelete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", false);

        verify(contentStorageService).deleteAll(segmentCaptor.capture());
        assertEquals(List.of(content1.getSegments().getFirst(), content2.getSegments().getFirst()), segmentCaptor.getValue());
        verify(deltaFileRepo).setContentDeletedByDidIn(uuidListCaptor.capture(), any(), eq("policy"));
        assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), uuidListCaptor.getValue());
    }

    @Test
    void testDeleteMetadata() {
        UUID did1 = UUID.randomUUID();
        Content content1 = new Content("name", "mediaType", new Segment(UUID.randomUUID(), did1));
        DeltaFile deltaFile1 = Util.buildDeltaFile(did1, List.of(content1));
        UUID did2 = UUID.randomUUID();
        Content content2 = new Content("name", "mediaType", new Segment(UUID.randomUUID(), did2));
        DeltaFile deltaFile2 = Util.buildDeltaFile(did2, List.of(content2));
        when(deltaFileRepo.findForTimedDelete(any(), any(), anyLong(), any(), anyBoolean(), anyInt())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.timedDelete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", true);

        verify(contentStorageService).deleteAll(segmentCaptor.capture());
        assertEquals(List.of(content1.getSegments().getFirst(), content2.getSegments().getFirst()), segmentCaptor.getValue());
        verify(deltaFileRepo, never()).saveAll(any());
        verify(deltaFileRepo).batchedBulkDeleteByDidIn(uuidListCaptor.capture());
        assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), uuidListCaptor.getValue());
    }

    @Test
    void testRequeue_actionFound() {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile(UUID.randomUUID());
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();
        deltaFileFlow.getActions().add(Action.builder().name("action").type(ActionType.EGRESS)
                .state(ActionState.QUEUED).modified(modified).build());

        ActionConfiguration actionConfiguration = new ActionConfiguration(null, ActionType.TRANSFORM, null);
        Mockito.when(egressFlowService.findActionConfig("myFlow", "action")).thenReturn(actionConfiguration);

        List<WrappedActionInput> actionInvocations = deltaFilesService.requeuedActionInputs(deltaFile, modified);
        Assertions.assertThat(actionInvocations).hasSize(1);
        Mockito.verifyNoInteractions(stateMachine);
    }

    @Test
    void testRequeue() {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile(UUID.randomUUID());
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        deltaFile.getFlows().getFirst().getActions().add(Action.builder().name("action").type(ActionType.EGRESS)
                .state(ActionState.QUEUED).modified(modified).build());

        ActionConfiguration actionConfiguration = new ActionConfiguration(null, ActionType.EGRESS, null);
        Mockito.when(transformFlowService.findActionConfig("myFlow", "action")).thenReturn(actionConfiguration);

        List<WrappedActionInput> actionInvocations = deltaFilesService.requeuedActionInputs(deltaFile, modified);
        Assertions.assertThat(actionInvocations).hasSize(1);
        Mockito.verifyNoInteractions(stateMachine);
    }

    @Test
    void testAnnotationDeltaFile() {
        DeltaFile deltaFile = Util.buildDeltaFile(UUID.randomUUID());
        deltaFile.addAnnotations(Map.of("key", "one"));

        Mockito.when(deltaFileCacheService.isCached(deltaFile.getDid())).thenReturn(true);
        Mockito.when(deltaFileCacheService.get(deltaFile.getDid())).thenReturn(deltaFile);
        Mockito.when(deltaFileRepo.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());

        deltaFilesService.addAnnotations(deltaFile.getDid(), Map.of("sys-ack", "true"), false);
        Mockito.verify(deltaFileCacheService).save(deltaFileCaptor.capture());

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
        DeltaFile deltaFile = Util.buildDeltaFile(UUID.randomUUID());
        deltaFile.addAnnotations(Map.of("key", "one"));

        Mockito.when(deltaFileCacheService.isCached(deltaFile.getDid())).thenReturn(true);
        Mockito.when(deltaFileCacheService.get(deltaFile.getDid())).thenReturn(deltaFile);

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
        Mockito.when(deltaFileRepo.existsById(did)).thenReturn(true);
        Mockito.when(deltaFileCacheService.isCached(did)).thenReturn(false);
        Mockito.when(deltaFileRepo.findByDidAndStageIn(eq(did), anyList())).thenReturn(Optional.empty());

        deltaFilesService.addAnnotations(did, Map.of("queued", "true"), false);

        Mockito.verify(queuedAnnotationRepo).save(queuedAnnotationCaptor.capture());
        Mockito.verify(deltaFileCacheService, never()).save(any());
        Mockito.verify(deltaFileRepo, never()).save(any());

        QueuedAnnotation after = queuedAnnotationCaptor.getValue();
        Assertions.assertThat(after.getAnnotations()).containsEntry("queued", "true");
    }

    @Test
    void testProcessQueuedAnnotations() {
        DeltaFile deltaFile = Util.buildDeltaFile(UUID.randomUUID());

        QueuedAnnotation queuedAnnotation = new QueuedAnnotation(deltaFile.getDid(), Map.of("queued", "true"), false);
        Mockito.when(queuedAnnotationRepo.findAllByOrderByTimeAsc()).thenReturn(List.of(queuedAnnotation));
        Mockito.when(deltaFileCacheService.isCached(deltaFile.getDid())).thenReturn(true);
        Mockito.when(deltaFileCacheService.get(deltaFile.getDid())).thenReturn(deltaFile);

        deltaFilesService.processQueuedAnnotations();
        Mockito.verify(queuedAnnotationRepo).deleteById(queuedAnnotation.getId());
        Assertions.assertThat(deltaFile.annotationMap()).containsEntry("queued", "true");
    }

    @Test
    void testDeleteContentAndMetadata() {
        Content content = new Content();
        UUID did = UUID.randomUUID();
        deltaFilesService.deleteContentAndMetadata(did, content);

        Mockito.verify(deltaFileRepo).deleteById(did);
        Mockito.verify(contentStorageService).delete(content);
    }

    @Test
    void testDeleteContentAndMetadata_dbFail() {
        Content content = new Content();
        UUID did = UUID.randomUUID();
        Mockito.doThrow(new RuntimeException("db fail")).when(deltaFileRepo).deleteById(did);
        deltaFilesService.deleteContentAndMetadata(did, content);

        // make sure content cleanup happens after a db failure
        Mockito.verify(contentStorageService).delete(content);
    }

    @Test
    void testEgress_addPendingAnnotations() {
        // TODO: this doesn't make a lot of sense, we should make sure annotations are added when the egress flow is added in the state machine
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setExpectedAnnotations(Set.of("a", "b"));
        Mockito.when(egressFlowService.hasFlow("flow")).thenReturn(true);
        Mockito.when(egressFlowService.getRunningFlowByName("flow")).thenReturn(egressFlow);

        DeltaFile deltaFile = Util.buildDeltaFile(UUID.randomUUID());
        DeltaFileFlow flow = DeltaFileFlow.builder().name("flow").type(FlowType.EGRESS).deltaFile(deltaFile).build();
        flow.setPendingAnnotations(Set.of("a", "b"));
        Action action = flow.queueAction( "egress", ActionType.EGRESS, false, OffsetDateTime.now(testClock));
        deltaFile.getFlows().add(flow);
        deltaFilesService.egress(deltaFile, flow, action, OffsetDateTime.now(testClock), OffsetDateTime.now(testClock));

        Assertions.assertThat(deltaFile.pendingAnnotationFlows()).hasSize(1);
        Assertions.assertThat(deltaFile.pendingAnnotationFlows().getFirst().getName()).isEqualTo("flow");
    }

    @Test
    void testEgress_addPendingAnnotationsIgnoreNotRunningException() {
        // TODO: this doesn't make a lot of sense, we should make sure annotations are added when the egress flow is added in the state machine
        Mockito.when(egressFlowService.hasFlow("flow")).thenReturn(true);
        Mockito.when(egressFlowService.getRunningFlowByName("flow")).thenThrow(new DgsEntityNotFoundException("not running"));

        DeltaFile deltaFile = Util.buildDeltaFile(UUID.randomUUID());
        DeltaFileFlow flow = deltaFile.getFlows().getFirst();
        Action action = flow.queueAction("egress", ActionType.EGRESS, false, OffsetDateTime.now(testClock));
        deltaFilesService.egress(deltaFile, flow, action, OffsetDateTime.now(testClock), OffsetDateTime.now(testClock));

        Assertions.assertThat(deltaFile.pendingAnnotationFlows()).isEmpty();
    }

    @Test
    void testGetPendingAnnotations() {
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setDid(UUID.randomUUID());
        deltaFile.setModified(OffsetDateTime.now());
        deltaFile.setFlows(Stream.of("a", "b", "c").map(this::deltaFileFlow).toList());
        Mockito.when(deltaFileRepo.findById(deltaFile.getDid())).thenReturn(Optional.of(deltaFile));
        Assertions.assertThat(deltaFilesService.getPendingAnnotations(deltaFile.getDid())).hasSize(3).contains("a", "b", "c");

        Mockito.when(deltaFileCacheService.isCached(deltaFile.getDid())).thenReturn(true);
        Mockito.when(deltaFileCacheService.get(deltaFile.getDid())).thenReturn(deltaFile);
        deltaFilesService.addAnnotations(deltaFile.getDid(), Map.of("b", "2"), false);
        Assertions.assertThat(deltaFilesService.getPendingAnnotations(deltaFile.getDid())).hasSize(2).contains("a", "c");
    }

    private DeltaFileFlow deltaFileFlow(String name) {
        DeltaFileFlow flow = new DeltaFileFlow();
        flow.setName(name);
        flow.setType(FlowType.EGRESS);
        flow.setPendingAnnotations(Set.of(name));
        flow.setActions(List.of(Action.builder().state(ActionState.COMPLETE).build()));
        return flow;
    }

    @Test
    void testGetPendingAnnotations_nullPendingList() {
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setDid(UUID.randomUUID());
        DeltaFileFlow deltaFileFlow = new DeltaFileFlow();
        deltaFile.setFlows(List.of(deltaFileFlow));

        Mockito.when(deltaFileRepo.findById(deltaFile.getDid())).thenReturn(Optional.of(deltaFile));
        Assertions.assertThat(deltaFilesService.getPendingAnnotations(deltaFile.getDid())).isEmpty();
    }

    @Test
    void testProcessActionEventsExceptionHandling() throws JsonProcessingException {
        Mockito.when(coreEventQueue.takeResult(Mockito.anyString()))
                .thenThrow(JsonProcessingException.class);
        assertFalse(deltaFilesService.processActionEvents("test"));
    }

    @Test
    void testErrorMetadataUnion() {
        DeltaFile deltaFile1 = Util.buildDeltaFile(UUID.randomUUID(), List.of());
        DeltaFileFlow deltaFileFlow1 = deltaFile1.getFlows().getFirst();
        deltaFileFlow1.setName("flow1");
        deltaFileFlow1.getActions().getFirst().setMetadata(Map.of("a", "1", "b", "2"));
        Action error1 = deltaFileFlow1.queueAction("TransformAction1", ActionType.TRANSFORM, false, OffsetDateTime.now());
        error1.error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");

        DeltaFile deltaFile2 = Util.buildDeltaFile(UUID.randomUUID(), List.of());
        DeltaFileFlow deltaFileFlow2 = deltaFile2.getFlows().getFirst();
        deltaFileFlow2.setName("flow1");
        deltaFileFlow2.getActions().getFirst().setMetadata(Map.of("a", "somethingElse", "c", "3"));
        Action error2 = deltaFileFlow2.queueAction("TransformAction1", ActionType.TRANSFORM, false, OffsetDateTime.now());
        error2.error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");

        DeltaFile deltaFile3 = Util.buildDeltaFile(UUID.randomUUID(), List.of());
        DeltaFileFlow deltaFileFlow3 = deltaFile3.getFlows().getFirst();
        deltaFileFlow3.setName("flow2");
        deltaFileFlow3.getActions().getFirst().setMetadata(Map.of("d", "4"));
        Action error3 = deltaFileFlow3.queueAction("TransformAction2", ActionType.TRANSFORM, false, OffsetDateTime.now());
        error3.error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");

        DeltaFile deltaFile4 = Util.buildDeltaFile(UUID.randomUUID(), List.of());
        DeltaFileFlow deltaFileFlow4 = deltaFile4.getFlows().getFirst();
        deltaFileFlow4.setName("flow3");
        deltaFileFlow4.getActions().getFirst().setMetadata(Map.of("e", "5"));
        deltaFileFlow4.queueAction("TransformAction3", ActionType.TRANSFORM, false, OffsetDateTime.now());

        List<DeltaFileFlow> deltaFileFlows = List.of(deltaFile1.getFlows().getFirst(), deltaFile2.getFlows().getFirst(), deltaFile3.getFlows().getFirst(), deltaFile4.getFlows().getFirst());
        when(deltaFileFlowRepo.findAllByDeltaFileIds(List.of(deltaFile1.getDid(), deltaFile2.getDid(), deltaFile3.getDid(), deltaFile4.getDid()))).thenReturn(deltaFileFlows);

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
        DeltaFile aggregate = Util.buildDeltaFile(UUID.randomUUID());
        aggregate.setJoinId(aggregate.getDid());
        DeltaFile parent1 = Util.buildDeltaFile(UUID.randomUUID());
        parent1.getFlows().getFirst().setJoinId(aggregate.getDid());
        DeltaFile parent2 = Util.buildDeltaFile(UUID.randomUUID());
        parent2.getFlows().getFirst().setJoinId(aggregate.getDid());
        aggregate.setParentDids(List.of(parent1.getDid(), parent2.getDid()));

        DeltaFileFlow flow = aggregate.getFlows().getFirst();
        flow.queueAction("join-transform", ActionType.TRANSFORM, false, OffsetDateTime.now());

        testClock.setInstant(flow.actionNamed("join-transform").orElseThrow().getModified().toInstant());
        when(queueManagementService.coldQueueActions()).thenReturn(Collections.emptySet());
        when(deltaFileRepo.updateForRequeue(OffsetDateTime.now(testClock),
                mockDeltaFiPropertiesService.getDeltaFiProperties().getRequeueDuration(), Collections.emptySet(),
                Collections.emptySet(), 5000))
                .thenReturn(List.of(aggregate));
        when(deltaFileRepo.findAllById(List.of(parent1.getDid(), parent2.getDid())))
                .thenReturn(List.of(parent1, parent2));

        ActionConfiguration ActionConfiguration =
                new ActionConfiguration("join-transform", ActionType.TRANSFORM, "org.deltafi.SomeJoiningTransformAction");
        ActionConfiguration.setJoin(new JoinConfiguration(Duration.parse("PT1H"), null, 3, null));
        when(transformFlowService.findActionConfig("myFlow", "join-transform"))
                .thenReturn(ActionConfiguration);

        deltaFilesService.requeue();

        verify(coreEventQueue).putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
        List<WrappedActionInput> enqueuedActions = actionInputListCaptor.getValue();
        assertEquals(1, enqueuedActions.size());
        assertEquals(List.of(parent1.getDid(), parent2.getDid()), enqueuedActions.getFirst().getActionContext().getJoinedDids());
        assertEquals(2, enqueuedActions.getFirst().getDeltaFileMessages().size());
    }

    @Test
    void applyResumePolicies() {
        deltaFilesService.applyResumePolicies(List.of("name1"));
        Mockito.verify(resumePolicyService, times(1)).refreshCache();
    }

    @Test
    void testReplayChild() {
        UUID uuid = UUID.randomUUID();
        DeltaFile deltaFile = Util.buildDeltaFile(uuid,"myFlow");

        DeltaFileFlow flow = deltaFile.addFlow("flow",  FlowType.TRANSFORM, new DeltaFileFlow(), OffsetDateTime.now(testClock));

        flow.addAction("parentAction1", ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        flow.addAction("parentAction2", ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        Action starter = flow.addAction("splitAction", ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);
        flow.addAction("childAction", ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        flow.addAction("childActionOldName", ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock)); // renamed action after the split should not cause an error

        TransformFlow flowConfig = new TransformFlow();
        flowConfig.setName("flow");
        flowConfig.setTransformActions(mockActions("parentAction1", "parentAction2", "splitAction", "childAction", "childActionNewName"));

        List<UUID> dids = List.of(uuid);
        Mockito.when(deltaFileRepo.findAllById(dids)).thenReturn(List.of(deltaFile));
        Mockito.when(transformFlowService.getFlowOrThrow("flow")).thenReturn(flowConfig);

        List<RetryResult> results = deltaFilesService.replay(dids, List.of(), List.of());

        assertEquals(1, results.size());
        RetryResult result = results.getFirst();
        assertTrue(result.getSuccess());

        Mockito.verify(stateMachine).advance(stateMachineInputCaptor.capture());

        List<StateMachineInput> stateMachines = stateMachineInputCaptor.getValue();
        assertEquals(1, stateMachines.size());
        DeltaFile child = stateMachines.getFirst().deltaFile();
        String nextAction = child.getFlows().getFirst().getNextPendingAction();
        DeltaFileFlow childFirstFlow = child.getFlows().getFirst();
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
        DeltaFile deltaFile = Util.buildDeltaFile(uuid,"myFlow");

        DeltaFileFlow flow = deltaFile.addFlow("flow",  FlowType.TRANSFORM, new DeltaFileFlow(), OffsetDateTime.now(testClock));

        flow.addAction("parentAction1", ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        flow.addAction("removedParentAction", ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        Action starter = flow.addAction("splitAction", ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);

        TransformFlow flowConfig = new TransformFlow();
        flowConfig.setName("flow");
        flowConfig.setTransformActions(mockActions("parentAction1", "splitAction", "childAction", "childAction2"));

        List<UUID> dids = List.of(uuid);
        Mockito.when(deltaFileRepo.findAllById(dids)).thenReturn(List.of(deltaFile));
        Mockito.when(transformFlowService.getFlowOrThrow("flow")).thenReturn(flowConfig);

        List<RetryResult> results = deltaFilesService.replay(dids, List.of(), List.of());

        assertEquals(1, results.size());
        RetryResult result = results.getFirst();
        assertFalse(result.getSuccess());
        assertEquals("The actions inherited from the parent DeltaFile for flow flow do not match the latest flow", result.getError());
    }

    @Test
    void testReplayChildAddedParentAction() {
        UUID uuid = UUID.randomUUID();
        DeltaFile deltaFile = Util.buildDeltaFile(uuid,"myFlow");

        DeltaFileFlow flow = deltaFile.addFlow("flow",  FlowType.TRANSFORM, new DeltaFileFlow(), OffsetDateTime.now(testClock));

        flow.addAction("parentAction1", ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        flow.addAction("parentAction2", ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        Action starter = flow.addAction("splitAction", ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);
        flow.addAction("childAction", ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);

        TransformFlow flowConfig = new TransformFlow();
        flowConfig.setName("flow");
        flowConfig.setTransformActions(mockActions("parentAction1", "parentAction2", "extraParentAction", "splitAction", "childAction", "childAction2"));

        List<UUID> dids = List.of(uuid);
        Mockito.when(deltaFileRepo.findAllById(dids)).thenReturn(List.of(deltaFile));
        Mockito.when(transformFlowService.getFlowOrThrow("flow")).thenReturn(flowConfig);

        List<RetryResult> results = deltaFilesService.replay(dids, List.of(), List.of());

        assertEquals(1, results.size());
        RetryResult result = results.getFirst();
        assertFalse(result.getSuccess());
        assertEquals("The actions inherited from the parent DeltaFile for flow flow do not match the latest flow", result.getError());
    }

    @Test
    void testReplayChildRenamedParentAction() {
        UUID uuid = UUID.randomUUID();
        DeltaFile deltaFile = Util.buildDeltaFile(uuid,"myFlow");

        DeltaFileFlow flow = deltaFile.addFlow("flow",  FlowType.TRANSFORM, new DeltaFileFlow(), OffsetDateTime.now(testClock));

        flow.addAction("parentAction1", ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        flow.addAction("parentActionOldName", ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        Action starter = flow.addAction("splitAction", ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);

        TransformFlow flowConfig = new TransformFlow();
        flowConfig.setName("flow");
        flowConfig.setTransformActions(mockActions("parentAction1", "parentActionNewName", "splitAction", "childAction", "childAction2"));

        List<UUID> dids = List.of(uuid);
        Mockito.when(deltaFileRepo.findAllById(dids)).thenReturn(List.of(deltaFile));
        Mockito.when(transformFlowService.getFlowOrThrow("flow")).thenReturn(flowConfig);

        List<RetryResult> results = deltaFilesService.replay(dids, List.of(), List.of());

        assertEquals(1, results.size());
        RetryResult result = results.getFirst();
        assertFalse(result.getSuccess());
        assertEquals("The actions inherited from the parent DeltaFile for flow flow do not match the latest flow", result.getError());
    }

    @Test
    void testReplayChildSplitActionRenamed() {
        UUID uuid = UUID.randomUUID();
        DeltaFile deltaFile = Util.buildDeltaFile(uuid,"myFlow");

        DeltaFileFlow flow = deltaFile.addFlow("flow",  FlowType.TRANSFORM, new DeltaFileFlow(), OffsetDateTime.now(testClock));

        flow.addAction("parentAction1", ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        flow.addAction("parentAction2", ActionType.TRANSFORM, ActionState.INHERITED, OffsetDateTime.now(testClock));
        Action starter = flow.addAction("splitActionOld", ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        starter.setReplayStart(true);
        flow.addAction("childAction", ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock));
        flow.addAction("childActionOldName", ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(testClock)); // renamed action after the split should not cause an error

        TransformFlow flowConfig = new TransformFlow();
        flowConfig.setName("flow");
        flowConfig.setTransformActions(mockActions("parentAction1", "parentAction2", "splitActionNew", "childAction", "childActionNewName"));

        List<UUID> dids = List.of(uuid);
        Mockito.when(deltaFileRepo.findAllById(dids)).thenReturn(List.of(deltaFile));
        Mockito.when(transformFlowService.getFlowOrThrow("flow")).thenReturn(flowConfig);

        List<RetryResult> results = deltaFilesService.replay(dids, List.of(), List.of());

        assertEquals(1, results.size());
        RetryResult result = results.getFirst();
        assertFalse(result.getSuccess());
        assertEquals("The flow flow no longer contains an action named splitActionOld where the replay would be begin", result.getError());
    }

    private List<ActionConfiguration> mockActions(String ... names) {
        return Stream.of(names).map(this::mockAction).toList();
    }

    private ActionConfiguration mockAction(String name) {
        return new ActionConfiguration(name, ActionType.TRANSFORM, "org.action." + name);
    }
}
