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
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.test.uuid.TestUUIDGenerator;
import org.deltafi.common.types.*;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.collect.ScheduledCollectService;
import org.deltafi.core.generated.types.DeltaFilesFilter;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.repo.QueuedAnnotationRepo;
import org.deltafi.core.types.*;
import org.deltafi.core.util.FlowBuilders;
import org.deltafi.core.util.Util;
import org.junit.jupiter.api.Disabled;
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

import static org.deltafi.core.repo.DeltaFileRepoImpl.FLOWS_INPUT_METADATA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeltaFilesServiceTest {
    private final TestClock testClock = new TestClock();
    private final MockDeltaFiPropertiesService mockDeltaFiPropertiesService = new MockDeltaFiPropertiesService();

    private final TransformFlowService transformFlowService;
    private final DataSourceService dataSourceService;
    private final EgressFlowService egressFlowService;
    private final StateMachine stateMachine;
    private final DeltaFileRepo deltaFileRepo;
    private final ActionEventQueue actionEventQueue;
    private final ContentStorageService contentStorageService;
    private final ResumePolicyService resumePolicyService;
    private final DeltaFileCacheService deltaFileCacheService;
    private final QueueManagementService queueManagementService;
    private final QueuedAnnotationRepo queuedAnnotationRepo;

    private final DeltaFilesService deltaFilesService;

    @Captor
    ArgumentCaptor<List<Segment>> segmentCaptor;

    @Captor
    ArgumentCaptor<List<String>> stringListCaptor;

    @Captor
    ArgumentCaptor<DeltaFile> deltaFileCaptor;

    @Captor
    ArgumentCaptor<List<ActionInput>> actionInputListCaptor;

    @Captor
    ArgumentCaptor<QueuedAnnotation> queuedAnnotationCaptor;

    DeltaFilesServiceTest(@Mock TransformFlowService transformFlowService,
            @Mock EgressFlowService egressFlowService, @Mock StateMachine stateMachine,
            @Mock DeltaFileRepo deltaFileRepo, @Mock ActionEventQueue actionEventQueue,
            @Mock ContentStorageService contentStorageService, @Mock ResumePolicyService resumePolicyService,
            @Mock MetricService metricService, @Mock CoreAuditLogger coreAuditLogger,
            @Mock DeltaFileCacheService deltaFileCacheService, @Mock DataSourceService dataSourceService,
            @Mock QueueManagementService queueManagementService, @Mock QueuedAnnotationRepo queuedAnnotationRepo,
            @Mock Environment environment, @Mock ScheduledCollectService scheduledCollectService) {
        this.transformFlowService = transformFlowService;
        this.egressFlowService = egressFlowService;
        this.stateMachine = stateMachine;
        this.deltaFileRepo = deltaFileRepo;
        this.actionEventQueue = actionEventQueue;
        this.contentStorageService = contentStorageService;
        this.resumePolicyService = resumePolicyService;
        this.deltaFileCacheService = deltaFileCacheService;
        this.queueManagementService = queueManagementService;
        this.queuedAnnotationRepo = queuedAnnotationRepo;
        this.dataSourceService = dataSourceService;

        UUIDGenerator uuidGenerator = new TestUUIDGenerator();
        deltaFilesService = new DeltaFilesService(testClock, transformFlowService, egressFlowService,
                mockDeltaFiPropertiesService, stateMachine, deltaFileRepo, actionEventQueue, contentStorageService,
                resumePolicyService, metricService, coreAuditLogger, new DidMutexService(), deltaFileCacheService,
                dataSourceService, queueManagementService, queuedAnnotationRepo, environment,
                scheduledCollectService, uuidGenerator);
    }

    @Test
    void setsAndGets() {
        RestDataSource dataSource = FlowBuilders.buildDataSource("theFlow");
        when(dataSourceService.getRunningRestDataSource(dataSource.getName())).thenReturn(dataSource);

        String did = UUID.randomUUID().toString();
        List<Content> content = Collections.singletonList(new Content("name", "mediaType"));
        IngressEventItem ingressInputItem = new IngressEventItem(did, "filename", dataSource.getName(),
                Map.of(), content);

        DeltaFile deltaFile = deltaFilesService.ingress(dataSource, ingressInputItem, OffsetDateTime.now(), OffsetDateTime.now());

        assertNotNull(deltaFile);
        DeltaFileFlow ingressFlow = deltaFile.getFlows().get(0);
        assertEquals(dataSource.getName(), ingressFlow.getName());
        assertEquals(did, deltaFile.getDid());
        assertTrue(ingressFlow.lastCompleteAction().isPresent());
    }

    @Test
    void getReturnsNullOnMissingDid() {
        assertNull(deltaFilesService.getDeltaFile("nonsense"));
    }

    @Test
    void getRawDeltaFile() throws JsonProcessingException {
        DeltaFileFlow ingressFlow = new DeltaFileFlow();
        Action action = Action.builder().metadata(Map.of()).build();
        ingressFlow.setActions(List.of(action));
        ingressFlow.setInput(new DeltaFileFlowInput());
        DeltaFile deltaFile = DeltaFile.builder()
                .did("hi")
                .created(OffsetDateTime.parse("2022-09-29T12:30:00+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .flows(List.of(ingressFlow))
                .build();
        when(deltaFileRepo.findById("hi")).thenReturn(Optional.ofNullable(deltaFile));
        String json = deltaFilesService.getRawDeltaFile("hi", false);
        assertTrue(json.contains("\"did\":\"hi\""));
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
                .did("hi")
                .flows(List.of(ingressFlow))
                .build();
        when(deltaFileRepo.findById("hi")).thenReturn(Optional.ofNullable(deltaFile));
        String json = deltaFilesService.getRawDeltaFile("hi", true);
        assertTrue(json.contains("  \"did\" : \"hi\",\n"));
        assertNotEquals(1, json.split("\n").length);
    }

    @Test
    void getRawReturnsNullOnMissingDid() throws JsonProcessingException {
        assertNull(deltaFilesService.getRawDeltaFile("nonsense", true));
        assertNull(deltaFilesService.getRawDeltaFile("nonsense", false));
    }

    @Test
    void testSourceMetadataUnion() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", List.of(), Map.of("k1", "1a", "k2", "val2"));
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", List.of(), Map.of("k1", "1b", "k3", "val3"));

        // Map.of disallows null keys or values, so do it the hard way
        DeltaFile deltaFile3 = Util.buildDeltaFile("3", List.of(), new HashMap<>());
        DeltaFileFlow ingressFlow = deltaFile3.getFlows().get(0);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("k2", "val2");
        metadata.put("k3", null);
        metadata.put("k4", "val4");
        ingressFlow.getInput().setMetadata(metadata);
        Action action = Action.builder().metadata(metadata).build();
        ingressFlow.getActions().add(action);

        List<String> dids = List.of("1", "2", "3", "4");
        DeltaFilesFilter filter = new DeltaFilesFilter();
        filter.setDids(dids);

        DeltaFiles deltaFiles = new DeltaFiles(0, 3, 3, List.of(deltaFile1, deltaFile2, deltaFile3));
        when(deltaFileRepo.deltaFiles(0, dids.size(), filter, null,
                List.of(FLOWS_INPUT_METADATA))).thenReturn(deltaFiles);

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
        Content content1 = new Content("name", "mediaType", new Segment("a", "1"));
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", List.of(content1));
        Content content2 = new Content("name", "mediaType", new Segment("b", "2"));
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", List.of(content2));
        when(deltaFileRepo.findForTimedDelete(any(), any(), anyLong(), any(), anyBoolean(), anyInt())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.timedDelete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", false);

        verify(contentStorageService).deleteAll(segmentCaptor.capture());
        assertEquals(List.of(content1.getSegments().get(0), content2.getSegments().get(0)), segmentCaptor.getValue());
        verify(deltaFileRepo).setContentDeletedByDidIn(stringListCaptor.capture(), any(), eq("policy"));
        assertEquals(List.of("1", "2"), stringListCaptor.getValue());
    }

    @Test
    void testDeleteMetadata() {
        Content content1 = new Content("name", "mediaType", new Segment("a", "1"));
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", List.of(content1));
        Content content2 = new Content("name", "mediaType", new Segment("b", "2"));
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", List.of(content2));
        when(deltaFileRepo.findForTimedDelete(any(), any(), anyLong(), any(), anyBoolean(), anyInt())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.timedDelete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", true);

        verify(contentStorageService).deleteAll(segmentCaptor.capture());
        assertEquals(List.of(content1.getSegments().get(0), content2.getSegments().get(0)), segmentCaptor.getValue());
        verify(deltaFileRepo, never()).saveAll(any());
        verify(deltaFileRepo).batchedBulkDeleteByDidIn(stringListCaptor.capture());
        assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), stringListCaptor.getValue());
    }

    @Test
    void testRequeue_actionFound() {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        DeltaFileFlow deltaFileFlow = deltaFile.getFlows().get(0);
        deltaFileFlow.getActions().add(Action.builder().name("action").type(ActionType.EGRESS)
                .state(ActionState.QUEUED).modified(modified).build());

        ActionConfiguration actionConfiguration = new TransformActionConfiguration(null, null);
        Mockito.when(egressFlowService.findActionConfig("myFlow", "action")).thenReturn(actionConfiguration);

        List<ActionInput> actionInvocations = deltaFilesService.requeuedActionInputs(deltaFile, modified);
        Assertions.assertThat(actionInvocations).hasSize(1);
        Mockito.verifyNoInteractions(stateMachine);
    }

    // TODO - restore after filling out egress flows again
    /*@Test
    void testRequeue_actionNotFound() throws MissingEgressFlowException {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.getActions().add(Action.builder().flow("flow").name("action").state(ActionState.QUEUED).modified(modified).build());

        List<ActionInvocation> actionInvocations = deltaFilesService.requeuedActionInvocations(deltaFile, modified);
        Assertions.assertThat(actionInvocations).isEmpty();

        ArgumentCaptor<DeltaFile> deltaFileCaptor = ArgumentCaptor.forClass(DeltaFile.class);
        Mockito.verify(stateMachine).advance(deltaFileCaptor.capture());

        List<DeltaFile> captured = deltaFileCaptor.getAllValues();

        DeltaFile erroredDeltaFile = captured.get(0);
        Optional<Action> maybeAction = erroredDeltaFile.actionNamed("flow", "action");
        Assertions.assertThat(maybeAction).isPresent();
        Action action = maybeAction.get();
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.ERROR);
        Assertions.assertThat(action.getErrorCause()).isEqualTo("Action named action is no longer running");
        Mockito.verify(metricService).increment(new Metric(FILES_ERRORED, 1).addTags(MetricsUtil.tagsFor("unknown", "action", deltaFile.getSourceInfo().getFlow(), null)));
    }*/

    @Test
    void testRequeue() {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        deltaFile.getFlows().get(0).getActions().add(Action.builder().name("action").type(ActionType.EGRESS)
                .state(ActionState.QUEUED).modified(modified).build());

        ActionConfiguration actionConfiguration = new EgressActionConfiguration(null, null);
        Mockito.when(transformFlowService.findActionConfig("myFlow", "action")).thenReturn(actionConfiguration);

        List<ActionInput> actionInvocations = deltaFilesService.requeuedActionInputs(deltaFile, modified);
        Assertions.assertThat(actionInvocations).hasSize(1);
        Mockito.verifyNoInteractions(stateMachine);
    }

    // TODO - test the new split behavior
    /*@Test
    void testReinjectCorrectChildFlow() {
        NormalizeFlow flow = new NormalizeFlow();
        LoadActionConfiguration actionConfig = new LoadActionConfiguration("loadAction", null);
        flow.setName(GOOD_NORMALIZE_FLOW);
        flow.setLoadAction(actionConfig);

        when(normalizeFlowService.hasRunningFlow(GOOD_NORMALIZE_FLOW)).thenReturn(true);
        when(normalizeFlowService.getRunningFlowByName(GOOD_NORMALIZE_FLOW)).thenReturn(flow);

        DeltaFile deltaFile = DeltaFile.builder()
                .sourceInfo(SourceInfo.builder().flow(GOOD_NORMALIZE_FLOW).build())
                .actions(new ArrayList<>(List.of(Action.builder().flow(GOOD_NORMALIZE_FLOW)
                        .name("loadAction").state(ActionState.QUEUED).build())))
                .did("00000000-0000-0000-00000-000000000000")
                .build();

        deltaFilesService.reinject(deltaFile,
                ActionEvent.builder()
                        .flow(GOOD_NORMALIZE_FLOW)
                        .action("loadAction")
                        .reinject(List.of(
                                ReinjectEvent.builder()
                                        .flow(GOOD_NORMALIZE_FLOW)
                                        .content(List.of(createContent("first"))).build(),
                                ReinjectEvent.builder()
                                        .flow(GOOD_NORMALIZE_FLOW)
                                        .content(List.of(createContent("second"))).build()))
                        .build());

        assertFalse(deltaFile.hasErroredAction());
        assertEquals(2, deltaFile.getChildDids().size());
        assertTrue(deltaFile.getActions().stream().noneMatch(a -> a.getState() == ActionState.ERROR));
    }

    private Content createContent(String did) {
        return new Content("name", APPLICATION_XML, new Segment(UUID.randomUUID().toString(), 0L, 32L, did));
    }*/

    @Test
    void testAnnotationDeltaFile() {
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.addAnnotations(Map.of("key", "one"));

        Mockito.when(deltaFileCacheService.isCached("1")).thenReturn(true);
        Mockito.when(deltaFileCacheService.get("1")).thenReturn(deltaFile);
        Mockito.when(deltaFileRepo.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());

        deltaFilesService.addAnnotations("1", Map.of("sys-ack", "true"), false);
        Mockito.verify(deltaFileCacheService).save(deltaFileCaptor.capture());

        DeltaFile after = deltaFileCaptor.getValue();
        Assertions.assertThat(after.getAnnotations()).hasSize(2).containsEntry("key", "one").containsEntry("sys-ack", "true");
        Assertions.assertThat(after.getAnnotationKeys()).hasSize(2).contains("key", "sys-ack");
    }

    @Test
    void testAnnotationDeltaFile_badDid() {
        Map<String, String> metadata = Map.of("sys-ack", "true");
        Assertions.assertThatThrownBy(() -> deltaFilesService.addAnnotations("did", metadata, true))
                .isInstanceOf(DgsEntityNotFoundException.class)
                .hasMessage("DeltaFile did not found.");
    }

    @Test
    void testAddAnnotationOverwrites() {
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.setAnnotations(new HashMap<>(Map.of("key", "one")));
        deltaFile.setAnnotationKeys(new HashSet<>(Set.of("key")));

        Mockito.when(deltaFileCacheService.isCached("1")).thenReturn(true);
        Mockito.when(deltaFileCacheService.get("1")).thenReturn(deltaFile);

        deltaFilesService.addAnnotations("1", Map.of("key", "changed"), false);
        Assertions.assertThat(deltaFile.getAnnotations()).hasSize(1).containsEntry("key", "one");

        deltaFilesService.addAnnotations("1", Map.of("key", "changed", "newKey", "value"), false);
        Assertions.assertThat(deltaFile.getAnnotations()).hasSize(2).containsEntry("key", "one").containsEntry("newKey", "value");
        Assertions.assertThat(deltaFile.getAnnotationKeys()).hasSize(2).contains("key", "newKey");

        deltaFilesService.addAnnotations("1", Map.of("key", "changed", "newKey", "value"), true);
        Assertions.assertThat(deltaFile.getAnnotations()).hasSize(2).containsEntry("key", "changed").containsEntry("newKey", "value");
    }

    @Test
    void testQueueAnnotation_whenDeltaFileNotReady() {
        Mockito.when(deltaFileRepo.existsById("2")).thenReturn(true);
        Mockito.when(deltaFileCacheService.isCached("2")).thenReturn(false);
        Mockito.when(deltaFileRepo.findByDidAndStageIn(eq("2"), anyList())).thenReturn(Optional.empty());

        deltaFilesService.addAnnotations("2", Map.of("queued", "true"), false);

        Mockito.verify(queuedAnnotationRepo).insert(queuedAnnotationCaptor.capture());
        Mockito.verify(deltaFileCacheService, never()).save(any());
        Mockito.verify(deltaFileRepo, never()).save(any());

        QueuedAnnotation after = queuedAnnotationCaptor.getValue();
        Assertions.assertThat(after.getAnnotations()).containsEntry("queued", "true");
    }

    @Test
    void testProcessQueuedAnnotations() {
        DeltaFile deltaFile = Util.buildDeltaFile("3");

        QueuedAnnotation queuedAnnotation = new QueuedAnnotation("3", Map.of("queued", "true"), false);
        Mockito.when(queuedAnnotationRepo.findAllByOrderByTimeAsc()).thenReturn(List.of(queuedAnnotation));
        Mockito.when(deltaFileCacheService.isCached("3")).thenReturn(true);
        Mockito.when(deltaFileCacheService.get("3")).thenReturn(deltaFile);

        deltaFilesService.processQueuedAnnotations();
        Mockito.verify(queuedAnnotationRepo).deleteById(queuedAnnotation.getId());
        Assertions.assertThat(deltaFile.getAnnotations()).containsEntry("queued", "true");
    }

    @Test
    void testDeleteContentAndMetadata() {
        Content content = new Content();
        deltaFilesService.deleteContentAndMetadata("a", content);

        Mockito.verify(deltaFileRepo).deleteById("a");
        Mockito.verify(contentStorageService).delete(content);
    }

    @Test
    void testDeleteContentAndMetadata_mongoFail() {
        Content content = new Content();
        Mockito.doThrow(new RuntimeException("mongo fail")).when(deltaFileRepo).deleteById("a");
        deltaFilesService.deleteContentAndMetadata("a", content);

        // make sure content cleanup happens after a mongo failure
        Mockito.verify(contentStorageService).delete(content);
    }

    @Test
    void testEgress_addPendingAnnotations() {
        // TODO: this doesn't make a lot of sense, we should make sure annotations are added when the egress flow is added in the state machine
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setExpectedAnnotations(Set.of("a", "b"));
        Mockito.when(egressFlowService.hasFlow("flow")).thenReturn(true);
        Mockito.when(egressFlowService.getRunningFlowByName("flow")).thenReturn(egressFlow);

        DeltaFile deltaFile = Util.buildDeltaFile("1");
        DeltaFileFlow flow = DeltaFileFlow.builder().name("flow").type(FlowType.EGRESS).build();
        flow.setPendingAnnotations(Set.of("a", "b"));
        Action action = flow.queueAction( "egress", ActionType.EGRESS, false, OffsetDateTime.now(testClock));
        deltaFile.getFlows().add(flow);
        deltaFilesService.egress(deltaFile, flow, action);

        Assertions.assertThat(deltaFile.pendingAnnotationFlows()).hasSize(1);
        Assertions.assertThat(deltaFile.pendingAnnotationFlows().get(0).getName()).isEqualTo("flow");
    }

    @Test
    void testEgress_addPendingAnnotationsIgnoreNotRunningException() {
        // TODO: this doesn't make a lot of sense, we should make sure annotations are added when the egress flow is added in the state machine
        Mockito.when(egressFlowService.hasFlow("flow")).thenReturn(true);
        Mockito.when(egressFlowService.getRunningFlowByName("flow")).thenThrow(new DgsEntityNotFoundException("not running"));

        DeltaFile deltaFile = Util.buildDeltaFile("1");
        DeltaFileFlow flow = deltaFile.getFlows().get(0);
        Action action = flow.queueAction("egress", ActionType.EGRESS, false, OffsetDateTime.now(testClock));
        deltaFilesService.egress(deltaFile, flow, action);

        Assertions.assertThat(deltaFile.pendingAnnotationFlows()).isEmpty();
    }

    @Test
    void testGetPendingAnnotations() {
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setModified(OffsetDateTime.now());
        deltaFile.setFlows(Stream.of("a", "b", "c").map(this::deltaFileFlow).toList());
        Mockito.when(deltaFileRepo.findById("1")).thenReturn(Optional.of(deltaFile));
        Assertions.assertThat(deltaFilesService.getPendingAnnotations("1")).hasSize(3).contains("a", "b", "c");

        Mockito.when(deltaFileCacheService.isCached("1")).thenReturn(true);
        Mockito.when(deltaFileCacheService.get("1")).thenReturn(deltaFile);
        deltaFilesService.addAnnotations("1", Map.of("b", "2"), false);
        Assertions.assertThat(deltaFilesService.getPendingAnnotations("1")).hasSize(2).contains("a", "c");
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
        DeltaFileFlow deltaFileFlow = new DeltaFileFlow();
        deltaFile.setFlows(List.of(deltaFileFlow));

        Mockito.when(deltaFileRepo.findById("did")).thenReturn(Optional.of(deltaFile));
        Assertions.assertThat(deltaFilesService.getPendingAnnotations("did")).isEmpty();
    }

    @Test
    void testProcessActionEventsExceptionHandling() throws JsonProcessingException {
        Mockito.when(actionEventQueue.takeResult(Mockito.anyString()))
                .thenThrow(JsonProcessingException.class);
        assertFalse(deltaFilesService.processActionEvents("test"));
    }

    @Test
    void testErrorMetadataUnion() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", List.of());
        DeltaFileFlow deltaFileFlow1 = deltaFile1.getFlows().get(0);
        deltaFileFlow1.setName("flow1");
        deltaFileFlow1.getActions().get(0).setMetadata(Map.of("a", "1", "b", "2"));
        Action error1 = deltaFileFlow1.queueAction("TransformAction1", ActionType.TRANSFORM, false, OffsetDateTime.now());
        error1.error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");

        DeltaFile deltaFile2 = Util.buildDeltaFile("2", List.of());
        DeltaFileFlow deltaFileFlow2 = deltaFile2.getFlows().get(0);
        deltaFileFlow2.setName("flow1");
        deltaFileFlow2.getActions().get(0).setMetadata(Map.of("a", "somethingElse", "c", "3"));
        Action error2 = deltaFileFlow2.queueAction("TransformAction1", ActionType.TRANSFORM, false, OffsetDateTime.now());
        error2.error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");

        DeltaFile deltaFile3 = Util.buildDeltaFile("2", List.of());
        DeltaFileFlow deltaFileFlow3 = deltaFile3.getFlows().get(0);
        deltaFileFlow3.setName("flow2");
        deltaFileFlow3.getActions().get(0).setMetadata(Map.of("d", "4"));
        Action error3 = deltaFileFlow3.queueAction("TransformAction2", ActionType.TRANSFORM, false, OffsetDateTime.now());
        error3.error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");

        DeltaFile deltaFile4 = Util.buildDeltaFile("3", List.of());
        DeltaFileFlow deltaFileFlow4 = deltaFile4.getFlows().get(0);
        deltaFileFlow4.setName("flow3");
        deltaFileFlow4.getActions().get(0).setMetadata(Map.of("e", "5"));
        deltaFileFlow4.queueAction("TransformAction3", ActionType.TRANSFORM, false, OffsetDateTime.now());

        DeltaFiles deltaFiles = new DeltaFiles(0, 4, 4, List.of(deltaFile1, deltaFile2, deltaFile3, deltaFile4));
        when(deltaFileRepo.deltaFiles(eq(0), eq(3), any(), any(), any())).thenReturn(deltaFiles);

        List<PerActionUniqueKeyValues> actionVals = deltaFilesService.errorMetadataUnion(List.of(deltaFile1.getDid(), deltaFile2.getDid(), deltaFile3.getDid()));

        assertEquals(2, actionVals.size());
        actionVals.sort(Comparator.comparing(PerActionUniqueKeyValues::getAction));
        assertEquals("flow1", actionVals.get(0).getFlow());
        assertEquals("TransformAction1", actionVals.get(0).getAction());
        assertEquals(3, actionVals.get(0).getKeyVals().size());
        assertEquals("a", actionVals.get(0).getKeyVals().get(0).getKey());
        assertEquals(List.of("1", "somethingElse"), actionVals.get(0).getKeyVals().get(0).getValues());
        assertEquals("b", actionVals.get(0).getKeyVals().get(1).getKey());
        assertEquals(List.of("2"), actionVals.get(0).getKeyVals().get(1).getValues());
        assertEquals("c", actionVals.get(0).getKeyVals().get(2).getKey());
        assertEquals(List.of("3"), actionVals.get(0).getKeyVals().get(2).getValues());
        assertEquals("TransformAction2", actionVals.get(1).getAction());
        assertEquals("flow2", actionVals.get(1).getFlow());
        assertEquals(1, actionVals.get(1).getKeyVals().size());
        assertEquals("d", actionVals.get(1).getKeyVals().get(0).getKey());
        assertEquals(List.of("4"), actionVals.get(1).getKeyVals().get(0).getValues());
    }

    @Test
    @Disabled("TODO: collect")
    void requeuesCollectedAction() {
        DeltaFile aggregate = Util.buildDeltaFile("3");
        aggregate.setAggregate(true);
        DeltaFile parent1 = Util.buildDeltaFile("1");
        DeltaFile parent2 = Util.buildDeltaFile("2");
        aggregate.setParentDids(List.of(parent1.getDid(), parent2.getDid()));

        DeltaFileFlow flow = aggregate.getFlows().get(0);
        flow.queueAction("collect-transform", ActionType.TRANSFORM, false, OffsetDateTime.now());

        testClock.setInstant(flow.actionNamed("collect-transform").orElseThrow().getModified().toInstant());
        when(queueManagementService.coldQueueActions()).thenReturn(Collections.emptySet());
        when(deltaFileRepo.updateForRequeue(eq(OffsetDateTime.now(testClock)),
                eq(mockDeltaFiPropertiesService.getDeltaFiProperties().getRequeueSeconds()), eq(Collections.emptySet()), eq(Collections.emptySet())))
                .thenReturn(List.of(aggregate));
        when(deltaFileRepo.findAllById(eq(List.of(parent1.getDid(), parent2.getDid()))))
                .thenReturn(List.of(parent1, parent2));

        TransformActionConfiguration transformActionConfiguration =
                new TransformActionConfiguration("collect-transform", "org.deltafi.SomeCollectingTransformAction");
        transformActionConfiguration.setCollect(new CollectConfiguration(Duration.parse("PT1H"), null, 3, null));
        when(transformFlowService.findActionConfig(eq("test-ingress"), eq("collect-transform")))
                .thenReturn(transformActionConfiguration);

        deltaFilesService.requeue();

        verify(actionEventQueue).putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
        List<ActionInput> enqueuedActions = actionInputListCaptor.getValue();
        assertEquals(1, enqueuedActions.size());
        assertEquals(List.of("1", "2"), enqueuedActions.get(0).getActionContext().getCollectedDids());
        assertEquals(2, enqueuedActions.get(0).getDeltaFileMessages().size());
    }

    @Test
    void applyResumePolicies() {
        deltaFilesService.applyResumePolicies(List.of("name1"));
        Mockito.verify(resumePolicyService, times(1)).refreshCache();
    }
}
