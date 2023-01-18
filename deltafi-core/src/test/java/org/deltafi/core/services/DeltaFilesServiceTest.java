/**
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
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.core.metrics.MetricRepository;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.common.types.*;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.Util;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.MissingEgressFlowException;
import org.deltafi.core.generated.types.DeltaFilesFilter;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.types.DeltaFiles;
import org.deltafi.core.types.IngressFlow;
import org.deltafi.core.types.UniqueKeyValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.deltafi.common.constant.DeltaFiConstants.FILES_ERRORED;
import static org.deltafi.core.repo.DeltaFileRepoImpl.SOURCE_INFO_METADATA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeltaFilesServiceTest {
    @Mock
    IngressFlowService flowService;

    @Mock
    DeltaFileRepo deltaFileRepo;

    @Mock
    ContentStorageService contentStorageService;

    @Captor
    ArgumentCaptor<List<Segment>> segmentCaptor;

    @Captor
    ArgumentCaptor<List<String>> stringListCaptor;

    @SuppressWarnings("unused")
    @Spy
    DeltaFiPropertiesService deltaFiPropertiesService = new MockDeltaFiPropertiesService();

    @SuppressWarnings("unused")
    @Mock
    StateMachine stateMachine;

    @Mock
    EgressFlowService egressFlowService;

    @SuppressWarnings("unused")
    @Mock
    ActionEventQueue actionEventQueue;

    @Mock
    @SuppressWarnings("unused")
    CoreAuditLogger coreAuditLogger;

    @InjectMocks
    DeltaFilesService deltaFilesService;

    @Mock
    MetricRepository metricRepository;

    @Test
    void setsAndGets() {
        final String flow = "theFlow";
        SourceInfo sourceInfo = new SourceInfo(null, flow, Map.of());

        String did = UUID.randomUUID().toString();

        List<Content> content = Collections.singletonList(Content.newBuilder().contentReference(new ContentReference("mediaType")).build());
        IngressEvent ingressInput = new IngressEvent(did, sourceInfo, content, OffsetDateTime.now());

        DeltaFile deltaFile = deltaFilesService.ingress(ingressInput);

        assertNotNull(deltaFile);
        assertEquals(flow, deltaFile.getSourceInfo().getFlow());
        assertEquals(did, deltaFile.getDid());
        assertNotNull(deltaFile.getLastProtocolLayer());
    }

    @Test
    void setThrowsOnMissingFlow() {
        SourceInfo sourceInfo = new SourceInfo(null, "nonsense", Map.of());
        List<Content> content = Collections.singletonList(Content.newBuilder().contentReference(new ContentReference("mediaType")).build());
        IngressEvent ingressInput = new IngressEvent("did", sourceInfo, content, OffsetDateTime.now());

        when(flowService.getRunningFlowByName(sourceInfo.getFlow())).thenThrow(new DgsEntityNotFoundException());
        assertThrows(DgsEntityNotFoundException.class, () -> deltaFilesService.ingress(ingressInput));
    }

    @Test
    void getReturnsNullOnMissingDid() {
        assertNull(deltaFilesService.getDeltaFile("nonsense"));
    }

    @Test
    void getRawDeltaFile() throws JsonProcessingException {
        DeltaFile deltaFile = DeltaFile.newBuilder()
                .did("hi")
                .created(OffsetDateTime.parse("2022-09-29T12:30:00+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .build();
        when(deltaFileRepo.findById("hi")).thenReturn(Optional.ofNullable(deltaFile));
        String json = deltaFilesService.getRawDeltaFile("hi", false);
        assertTrue(json.contains("\"did\":\"hi\""));
        assertTrue(json.contains("\"created\":\"2022-09-29T11:30:00.000Z\""));
        assertEquals(1, json.split("\n").length);
    }

    @Test
    void getRawDeltaFilePretty() throws JsonProcessingException {
        DeltaFile deltaFile = DeltaFile.newBuilder().did("hi").build();
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
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", Map.of("k1", "1a", "k2", "val2"));
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", Map.of("k1", "1b", "k3", "val3"));

        // Map.of disallows null keys or values, so do it the hard way
        DeltaFile deltaFile3 = Util.buildDeltaFile("3", new HashMap<>());
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
        DeltaFile deltaFile1 = Util.buildDeltaFile("1");
        ContentReference cr1 = new ContentReference("mediaType", new Segment("a", "1"));
        deltaFile1.getProtocolStack().add(ProtocolLayer.builder().content(List.of(Content.newBuilder().contentReference(cr1).build())).build());
        ContentReference cr2 = new ContentReference("mediaType", new Segment("b", "2"));
        DeltaFile deltaFile2 = Util.buildDeltaFile("2");
        deltaFile2.getFormattedData().add(FormattedData.newBuilder().contentReference(cr2).build());
        when(deltaFileRepo.findForDelete(any(), any(), anyLong(), any(), any(), anyBoolean(), anyInt())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.delete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", false, 10);

        verify(contentStorageService).deleteAll(segmentCaptor.capture());
        assertEquals(List.of(cr1.getSegments().get(0), cr2.getSegments().get(0)), segmentCaptor.getValue());
        verify(deltaFileRepo).setContentDeletedByDidIn(stringListCaptor.capture(), any(), eq("policy"));
        assertEquals(List.of("1", "2"), stringListCaptor.getValue());
    }

    @Test
    void testDeleteMetadata() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1");
        ContentReference cr1 = new ContentReference("mediaType", new Segment("a", "1"));
        deltaFile1.getProtocolStack().add(ProtocolLayer.builder().content(List.of(Content.newBuilder().contentReference(cr1).build())).build());
        ContentReference cr2 = new ContentReference("mediaType", new Segment("b", "2"));
        DeltaFile deltaFile2 = Util.buildDeltaFile("2");
        deltaFile2.getFormattedData().add(FormattedData.newBuilder().contentReference(cr2).build());
        when(deltaFileRepo.findForDelete(any(), any(), anyLong(), any(), any(), anyBoolean(), anyInt())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.delete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", true, 10);

        verify(contentStorageService).deleteAll(segmentCaptor.capture());
        assertEquals(List.of(cr1.getSegments().get(0), cr2.getSegments().get(0)), segmentCaptor.getValue());
        verify(deltaFileRepo, never()).saveAll(any());
        verify(deltaFileRepo).deleteByDidIn(stringListCaptor.capture());
        assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), stringListCaptor.getValue());
    }

    @Test
    void testRequeue_actionFound() {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.getActions().add(Action.newBuilder().name("action").state(ActionState.QUEUED).modified(modified).build());

        ActionConfiguration actionConfiguration = new FormatActionConfiguration(null, null, null);
        Mockito.when(egressFlowService.findActionConfig("action")).thenReturn(actionConfiguration);

        List<ActionInput> toQueue = deltaFilesService.requeuedActionInput(deltaFile, modified);
        Assertions.assertThat(toQueue).hasSize(1);
        Mockito.verifyNoInteractions(stateMachine);
    }

    @Test
    void testRequeue_actionNotFound() throws MissingEgressFlowException {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.getActions().add(Action.newBuilder().name("action").state(ActionState.QUEUED).modified(modified).build());

        List<ActionInput> toQueue = deltaFilesService.requeuedActionInput(deltaFile, modified);
        Assertions.assertThat(toQueue).isEmpty();

        ArgumentCaptor<DeltaFile> deltaFileCaptor = ArgumentCaptor.forClass(DeltaFile.class);
        Mockito.verify(stateMachine).advance(deltaFileCaptor.capture());

        List<DeltaFile> captured = deltaFileCaptor.getAllValues();

        DeltaFile erroredDeltaFile = captured.get(0);
        Optional<Action> maybeAction = erroredDeltaFile.actionNamed("action");
        Assertions.assertThat(maybeAction).isPresent();
        Action action = maybeAction.get();
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.ERROR);
        Assertions.assertThat(action.getErrorCause()).isEqualTo("Action named action is no longer running");
        Mockito.verify(metricRepository).increment(new Metric(FILES_ERRORED, 1).addTags(MetricsUtil.tagsFor("unknown", "action", deltaFile.getSourceInfo().getFlow(), null)));
    }

    @Test
    void testSplitNoChildFlow() {
        IngressFlow flow = new IngressFlow();
        LoadActionConfiguration actionConfig = new LoadActionConfiguration("loadAction", null);
        flow.setName("loadAction");
        flow.setLoadAction(actionConfig);

        // "good" flow is available
        when(flowService.getRunningFlowByName(eq("good"))).thenReturn(flow);
        // "bad" flow is not available
        when(flowService.getRunningFlowByName(eq("bad"))).thenThrow(new DgsEntityNotFoundException());

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .sourceInfo(SourceInfo.builder().flow("good").build())
                .actions(new ArrayList<>(List.of(Action.newBuilder()
                        .name("loadAction").state(ActionState.QUEUED).build())))
                .did("00000000-0000-0000-00000-000000000000")
                .build();

        deltaFilesService.split(deltaFile, ActionEventInput.newBuilder()
                .action("loadAction")
                .split(List.of(
                        SplitEvent.newBuilder().sourceInfo(
                                SourceInfo.builder().flow("good").build())
                                .content(List.of(Content.newBuilder().contentReference(createContentReference("first"))
                                        .build())).build(),
                        SplitEvent.newBuilder().sourceInfo(
                                SourceInfo.builder().flow("bad").build())
                                .content(List.of(Content.newBuilder().contentReference(createContentReference("second"))
                                        .build())).build())).build());

        assertTrue(deltaFile.hasErroredAction());
        assertTrue(deltaFile.getActions().stream().filter(a -> a.getState()==ActionState.ERROR).map(Action::getErrorCause)
                .allMatch(DeltaFilesService.NO_CHILD_INGRESS_CONFIGURED_CAUSE::equals));
        assertTrue(deltaFile.getActions().stream().filter(a -> a.getState()==ActionState.ERROR).map(Action::getErrorContext)
                .allMatch(ec -> ec.startsWith(DeltaFilesService.NO_CHILD_INGRESS_CONFIGURED_CONTEXT)));
    }

    @Test
    void testSplitCorrectChildFlow() {
        IngressFlow flow = new IngressFlow();
        LoadActionConfiguration actionConfig = new LoadActionConfiguration("loadAction", null);
        flow.setName("loadAction");
        flow.setLoadAction(actionConfig);

        // "good" flow is available
        when(flowService.getRunningFlowByName(eq("good"))).thenReturn(flow);
        // "bad" flow is not available

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .sourceInfo(SourceInfo.builder().flow("good").build())
                .actions(new ArrayList<>(List.of(Action.newBuilder()
                        .name("loadAction").state(ActionState.QUEUED).build())))
                .did("00000000-0000-0000-00000-000000000000")
                .build();

        deltaFilesService.split(deltaFile, ActionEventInput.newBuilder()
                .action("loadAction")
                .split(List.of(
                        SplitEvent.newBuilder().sourceInfo(
                                        SourceInfo.builder().flow("good").build())
                                .content(List.of(Content.newBuilder().contentReference(createContentReference("first"))
                                        .build())).build(),
                        SplitEvent.newBuilder().sourceInfo(
                                        SourceInfo.builder().flow("good").build())
                                .content(List.of(Content.newBuilder().contentReference(createContentReference("second"))
                                        .build())).build()))
                .build());

        assertFalse(deltaFile.hasErroredAction());
        assertTrue(deltaFile.getActions().stream().noneMatch(a -> a.getState()==ActionState.ERROR));
    }

    private ContentReference createContentReference(String did) {
        return new ContentReference(APPLICATION_XML, new Segment(UUID.randomUUID().toString(), 0L, 32L, did));
    }
}
