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
import org.deltafi.common.types.*;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.MissingEgressFlowException;
import org.deltafi.core.generated.types.DeltaFilesFilter;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.types.*;
import org.deltafi.core.util.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.deltafi.common.constant.DeltaFiConstants.FILES_ERRORED;
import static org.deltafi.core.repo.DeltaFileRepoImpl.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeltaFilesServiceTest {

    private static final String ERRORS_EXCEEDED_TRANSFORM_FLOW = "errorsExceededFlow";
    private static final String GOOD_NORMALIZE_FLOW = "goodNormalizeFlow";

    private final NormalizeFlowService normalizeFlowService;
    private final EgressFlowService egressFlowService;
    private final TransformFlowService transformFlowService;
    private final StateMachine stateMachine;
    private final ActionEventQueue actionEventQueue;
    private final DeltaFileRepo deltaFileRepo;
    private final ContentStorageService contentStorageService;
    private final MetricService metricService;
    private final DeltaFileCacheService deltaFileCacheService;

    private final DeltaFilesService deltaFilesService;

    @Captor
    ArgumentCaptor<List<Segment>> segmentCaptor;

    @Captor
    ArgumentCaptor<List<String>> stringListCaptor;
    @Captor
    ArgumentCaptor<DeltaFile> deltaFileCaptor;

    DeltaFilesServiceTest(@Mock NormalizeFlowService normalizeFlowService, @Mock EnrichFlowService enrichFlowService,
                          @Mock EgressFlowService egressFlowService, @Mock TransformFlowService transformFlowService, @Mock StateMachine stateMachine,
                          @Mock DeltaFileRepo deltaFileRepo, @Mock ActionEventQueue actionEventQueue, @Mock ResumePolicyService resumePolicyService,
                          @Mock ContentStorageService contentStorageService, @Mock MetricService metricService,
                          @Mock CoreAuditLogger coreAuditLogger, @Mock IdentityService identityService,
                          @Mock DeltaFileCacheService deltaFileCacheService) {
        this.normalizeFlowService = normalizeFlowService;
        this.egressFlowService = egressFlowService;
        this.transformFlowService = transformFlowService;
        this.stateMachine = stateMachine;
        this.deltaFileRepo = deltaFileRepo;
        this.actionEventQueue = actionEventQueue;
        this.contentStorageService = contentStorageService;
        this.metricService = metricService;
        this.deltaFileCacheService = deltaFileCacheService;

        Clock clock = new TestClock();
        deltaFilesService = new DeltaFilesService(clock, normalizeFlowService, enrichFlowService,
                egressFlowService, transformFlowService, new MockDeltaFiPropertiesService(), stateMachine,
                deltaFileRepo, actionEventQueue, contentStorageService, resumePolicyService, metricService,
                coreAuditLogger, identityService, new DidMutexService(), deltaFileCacheService);
    }

    @Test
    void setsAndGets() {
        NormalizeFlow normalizeFlow = new NormalizeFlow();
        normalizeFlow.setName("theFlow");
        when(normalizeFlowService.getRunningFlowByName(normalizeFlow.getName())).thenReturn(normalizeFlow);

        String did = UUID.randomUUID().toString();
        SourceInfo sourceInfo = new SourceInfo("filename", normalizeFlow.getName(), Map.of());
        List<Content> content = Collections.singletonList(new Content("name", "mediaType"));
        IngressEvent ingressInput = new IngressEvent(did, sourceInfo, content, OffsetDateTime.now());

        DeltaFile deltaFile = deltaFilesService.ingress(ingressInput);

        assertNotNull(deltaFile);
        assertEquals(normalizeFlow.getName(), deltaFile.getSourceInfo().getFlow());
        assertEquals(did, deltaFile.getDid());
        assertNotNull(deltaFile.lastCompleteDataAmendedAction());
    }

    @Test
    void getReturnsNullOnMissingDid() {
        assertNull(deltaFilesService.getDeltaFile("nonsense"));
    }

    @Test
    void getRawDeltaFile() throws JsonProcessingException {
        DeltaFile deltaFile = DeltaFile.builder()
                .did("hi")
                .created(OffsetDateTime.parse("2022-09-29T12:30:00+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .sourceInfo(SourceInfo.builder().metadata(Map.of()).build())
                .build();
        when(deltaFileRepo.findById("hi")).thenReturn(Optional.ofNullable(deltaFile));
        String json = deltaFilesService.getRawDeltaFile("hi", false);
        assertTrue(json.contains("\"did\":\"hi\""));
        assertTrue(json.contains("\"created\":\"2022-09-29T11:30:00.000Z\""));
        assertEquals(1, json.split("\n").length);
    }

    @Test
    void getRawDeltaFilePretty() throws JsonProcessingException {
        DeltaFile deltaFile = DeltaFile.builder()
                .did("hi")
                .sourceInfo(SourceInfo.builder().metadata(Map.of()).build())
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
        deltaFile3.getSourceInfo().addMetadata("k2", "val2");
        deltaFile3.getSourceInfo().addMetadata("k3", null);
        deltaFile3.getSourceInfo().addMetadata("k4", "val4");

        List<String> dids = List.of("1", "2", "3", "4");
        DeltaFilesFilter filter = new DeltaFilesFilter();
        filter.setDids(dids);

        DeltaFiles deltaFiles = new DeltaFiles(0, 3, 3, List.of(deltaFile1, deltaFile2, deltaFile3));
        when(deltaFileRepo.deltaFiles(0, dids.size(), filter, null,
                List.of(SOURCE_INFO_METADATA))).thenReturn(deltaFiles);

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
        when(deltaFileRepo.findForDelete(any(), any(), anyLong(), any(), any(), anyBoolean(), anyInt())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.delete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", false);

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
        when(deltaFileRepo.findForDelete(any(), any(), anyLong(), any(), any(), anyBoolean(), anyInt())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.delete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", true);

        verify(contentStorageService).deleteAll(segmentCaptor.capture());
        assertEquals(List.of(content1.getSegments().get(0), content2.getSegments().get(0)), segmentCaptor.getValue());
        verify(deltaFileRepo, never()).saveAll(any());
        verify(deltaFileRepo).deleteByDidIn(stringListCaptor.capture());
        assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), stringListCaptor.getValue());
    }

    @Test
    void testRequeue_actionFound() {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.getActions().add(Action.builder().flow("myFlow").name("action").type(ActionType.EGRESS)
                .state(ActionState.QUEUED).modified(modified).build());

        ActionConfiguration actionConfiguration = new FormatActionConfiguration(null, null, null);
        Mockito.when(egressFlowService.findActionConfig("myFlow", "action")).thenReturn(actionConfiguration);

        List<ActionInput> toQueue = deltaFilesService.requeuedActionInput(deltaFile, modified);
        Assertions.assertThat(toQueue).hasSize(1);
        Mockito.verifyNoInteractions(stateMachine);
    }

    @Test
    void testRequeue_actionNotFound() throws MissingEgressFlowException {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.getActions().add(Action.builder().flow("flow").name("action").state(ActionState.QUEUED).modified(modified).build());

        List<ActionInput> toQueue = deltaFilesService.requeuedActionInput(deltaFile, modified);
        Assertions.assertThat(toQueue).isEmpty();

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
    }

    @Test
    void testRequeue_transformFlow() {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.getSourceInfo().setProcessingType(ProcessingType.TRANSFORMATION);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.getActions().add(Action.builder().flow("myFlow").name("action").type(ActionType.EGRESS)
                .state(ActionState.QUEUED).modified(modified).build());

        ActionConfiguration actionConfiguration = new EgressActionConfiguration(null, null);
        Mockito.when(transformFlowService.findActionConfig("myFlow", "action")).thenReturn(actionConfiguration);

        List<ActionInput> toQueue = deltaFilesService.requeuedActionInput(deltaFile, modified);
        Assertions.assertThat(toQueue).hasSize(1);
        Mockito.verifyNoInteractions(stateMachine);
    }

    @Test
    void testReinjectNoChildFlow() {
        NormalizeFlow flow = new NormalizeFlow();
        LoadActionConfiguration actionConfig = new LoadActionConfiguration("loadAction", null);
        flow.setName(GOOD_NORMALIZE_FLOW);
        flow.setLoadAction(actionConfig);

        // "good" flow is running
        when(normalizeFlowService.hasRunningFlow(GOOD_NORMALIZE_FLOW)).thenReturn(true);
        when(normalizeFlowService.getRunningFlowByName(GOOD_NORMALIZE_FLOW)).thenReturn(flow);
        // "bad" flow is not running
        when(normalizeFlowService.hasRunningFlow("bad")).thenReturn(false);

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
                                        .content(List.of(createContent("first")))
                                        .build(),
                                ReinjectEvent.builder()
                                        .flow("bad")
                                        .content(List.of(createContent("second"))).build()))
                        .build());

        assertTrue(deltaFile.hasErroredAction());
        assertEquals(0, deltaFile.getChildDids().size());
        assertTrue(deltaFile.getActions().stream().filter(a -> a.getState() == ActionState.ERROR).map(Action::getErrorCause)
                .allMatch(DeltaFilesService.NO_CHILD_INGRESS_CONFIGURED_CAUSE::equals));
        assertTrue(deltaFile.getActions().stream().filter(a -> a.getState() == ActionState.ERROR).map(Action::getErrorContext)
                .allMatch(ec -> ec.startsWith(DeltaFilesService.NO_CHILD_INGRESS_CONFIGURED_CONTEXT)));
    }

    @Test
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

    @Test
    void testReinjectErrorsExceeded() {
        NormalizeFlow goodFlow = new NormalizeFlow();
        LoadActionConfiguration loadActionConfig = new LoadActionConfiguration("loadAction", null);
        goodFlow.setName(GOOD_NORMALIZE_FLOW);
        goodFlow.setLoadAction(loadActionConfig);

        TransformFlow errorsFlow = new TransformFlow();
        TransformActionConfiguration transformActionConfig = new TransformActionConfiguration("transformAction", null);
        errorsFlow.setName(ERRORS_EXCEEDED_TRANSFORM_FLOW);
        errorsFlow.setTransformActions(List.of(transformActionConfig));

        when(normalizeFlowService.flowErrorsExceeded()).thenReturn(Set.of("other"));
        when(transformFlowService.flowErrorsExceeded()).thenReturn(Set.of(ERRORS_EXCEEDED_TRANSFORM_FLOW));

        when(normalizeFlowService.hasRunningFlow(GOOD_NORMALIZE_FLOW)).thenReturn(true);
        when(normalizeFlowService.hasRunningFlow(ERRORS_EXCEEDED_TRANSFORM_FLOW)).thenReturn(false);
        when(transformFlowService.hasRunningFlow(ERRORS_EXCEEDED_TRANSFORM_FLOW)).thenReturn(true);
        when(normalizeFlowService.getRunningFlowByName(GOOD_NORMALIZE_FLOW)).thenReturn(goodFlow);

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
                                        .flow(ERRORS_EXCEEDED_TRANSFORM_FLOW)
                                        .content(List.of(createContent("second"))).build()))
                        .build());

        assertTrue(deltaFile.hasErroredAction());
        assertEquals(0, deltaFile.getChildDids().size());
        assertTrue(deltaFile.getActions().stream().filter(a -> a.getState() == ActionState.ERROR).map(Action::getErrorCause)
                .allMatch(DeltaFilesService.CHILD_FLOW_INGRESS_DISABLED_CAUSE::equals));
        assertTrue(deltaFile.getActions().stream().filter(a -> a.getState() == ActionState.ERROR).map(Action::getErrorContext)
                .allMatch(ec -> ec.startsWith(DeltaFilesService.CHILD_FLOW_INGRESS_DISABLED_CONTEXT)));
    }

    @Test
    void testAnnotationDeltaFile() {
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.addAnnotations(Map.of("key", "one"));

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
        Set<String> expectedAnnotations = Set.of("a", "b");
        TransformFlow transformFlow = new TransformFlow();
        transformFlow.setExpectedAnnotations(expectedAnnotations);
        Mockito.when(transformFlowService.hasFlow("flow")).thenReturn(true);
        Mockito.when(transformFlowService.getRunningFlowByName("flow")).thenReturn(transformFlow);

        ActionEvent actionEvent = new ActionEvent();
        actionEvent.setFlow("flow");
        actionEvent.setAction("transform");
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.setSourceInfo(SourceInfo.builder().processingType(ProcessingType.TRANSFORMATION).build());
        deltaFile.queueAction("flow", "transform", ActionType.TRANSFORM);
        deltaFilesService.egress(deltaFile, actionEvent);

        Assertions.assertThat(deltaFile.getPendingAnnotationsForFlows()).hasSize(1).contains("flow");
    }

    @Test
    void testEgress_addPendingAnnotationsIgnoreNotRunningException() {
        Mockito.when(egressFlowService.hasFlow("flow")).thenReturn(true);
        Mockito.when(egressFlowService.getRunningFlowByName("flow")).thenThrow(new DgsEntityNotFoundException("not running"));

        ActionEvent actionEvent = new ActionEvent();
        actionEvent.setFlow("flow");
        actionEvent.setAction("egress");
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.queueAction("flow", "egress", ActionType.EGRESS);
        deltaFilesService.egress(deltaFile, actionEvent);

        Assertions.assertThat(deltaFile.getPendingAnnotationsForFlows()).isNull();
    }

    @Test
    void testGetPendingAnnotations() {
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setSourceInfo(SourceInfo.builder().processingType(ProcessingType.NORMALIZATION).build());
        deltaFile.setPendingAnnotationsForFlows(Set.of("a", "b", "c"));
        deltaFile.addAnnotations(Map.of("2", "2"));

        EgressFlow a = new EgressFlow();
        a.setExpectedAnnotations(Set.of("1", "2"));

        EgressFlow b = new EgressFlow();
        b.setExpectedAnnotations(Set.of("3"));

        EgressFlow c = new EgressFlow(); // test handling null expectedAnnotations on the flow

        Mockito.when(deltaFileRepo.findById("did")).thenReturn(Optional.of(deltaFile));
        Mockito.when(egressFlowService.getRunningFlowByName("a")).thenReturn(a);
        Mockito.when(egressFlowService.getRunningFlowByName("b")).thenReturn(b);
        Mockito.when(egressFlowService.getRunningFlowByName("c")).thenReturn(c);

        Assertions.assertThat(deltaFilesService.getPendingAnnotations("did")).hasSize(2).contains("1", "3");
    }

    @Test
    void testGetPendingAnnotations_nullPendingList() {
        DeltaFile deltaFile = new DeltaFile();

        Mockito.when(deltaFileRepo.findById("did")).thenReturn(Optional.of(deltaFile));
        Assertions.assertThat(deltaFilesService.getPendingAnnotations("did")).isEmpty();
    }

    @Test
    void testProcessActionEventsExceptionHandling() throws JsonProcessingException {
        Mockito.when(actionEventQueue.takeResult(Mockito.anyString()))
                .thenThrow(JsonProcessingException.class);
        assertFalse(deltaFilesService.processActionEvents("test"));
    }

    private Content createContent(String did) {
        return new Content("name", APPLICATION_XML, new Segment(UUID.randomUUID().toString(), 0L, 32L, did));
    }

    @Test
    void testErrorMetadataUnion() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", List.of());
        deltaFile1.getActions().get(0).setMetadata(Map.of("a", "1", "b", "2"));
        deltaFile1.queueAction("flow1", "TransformAction1", ActionType.TRANSFORM);
        deltaFile1.errorAction("flow1", "TransformAction1", OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");

        DeltaFile deltaFile2 = Util.buildDeltaFile("2", List.of());
        deltaFile2.getActions().get(0).setMetadata(Map.of("a", "somethingElse", "c", "3"));
        deltaFile2.queueAction("flow1", "TransformAction1", ActionType.TRANSFORM);
        deltaFile2.errorAction("flow1", "TransformAction1", OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");

        DeltaFile deltaFile3 = Util.buildDeltaFile("2", List.of());
        deltaFile3.getActions().get(0).setMetadata(Map.of("d", "4"));
        deltaFile3.queueAction("flow2", "TransformAction2", ActionType.TRANSFORM);
        deltaFile3.errorAction("flow2", "TransformAction2", OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");

        DeltaFile deltaFile4 = Util.buildDeltaFile("3", List.of());
        deltaFile4.getActions().get(0).setMetadata(Map.of("e", "5"));
        deltaFile4.queueAction("flow3", "TransformAction3", ActionType.TRANSFORM);

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
}
