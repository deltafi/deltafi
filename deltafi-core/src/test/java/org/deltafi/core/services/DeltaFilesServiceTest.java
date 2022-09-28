/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.assertj.core.api.Assertions;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.metrics.MetricRepository;
import org.deltafi.common.metrics.MetricsUtil;
import org.deltafi.common.types.*;
import org.deltafi.core.Util;
import org.deltafi.core.configuration.ActionConfiguration;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.FormatActionConfiguration;
import org.deltafi.core.configuration.LoadActionConfiguration;
import org.deltafi.core.exceptions.MissingEgressFlowException;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.types.DeltaFiles;
import org.deltafi.core.types.IngressFlow;
import org.deltafi.core.types.UniqueKeyValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.*;

import static org.deltafi.common.metrics.MetricsUtil.FILES_ERRORED;
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
    ArgumentCaptor<List<ContentReference>> contentReferenceCaptor;

    @Captor
    ArgumentCaptor<List<DeltaFile>> deltaFileListCaptor;

    @SuppressWarnings("unused")
    @Spy
    DeltaFiProperties deltaFiProperties = new DeltaFiProperties();

    @SuppressWarnings("unused")
    @Mock
    StateMachine stateMachine;

    @Mock
    EgressFlowService egressFlowService;

    @Mock
    ActionEventQueue actionEventQueue;

    @InjectMocks
    DeltaFilesService deltaFilesService;

    @Mock
    MetricRepository metricRepository;

    @Test
    void setsAndGets() {
        final String flow = "theFlow";
        SourceInfo sourceInfo = new SourceInfo(null, flow, List.of());

        String did = UUID.randomUUID().toString();

        List<Content> content = Collections.singletonList(Content.newBuilder().contentReference(new ContentReference()).build());
        IngressInput ingressInput = new IngressInput(did, sourceInfo, content, OffsetDateTime.now());

        DeltaFile deltaFile = deltaFilesService.ingress(ingressInput);

        assertNotNull(deltaFile);
        assertEquals(flow, deltaFile.getSourceInfo().getFlow());
        assertEquals(did, deltaFile.getDid());
        assertNotNull(deltaFile.getLastProtocolLayer());
    }

    @Test
    void setThrowsOnMissingFlow() {
        SourceInfo sourceInfo = new SourceInfo(null, "nonsense", List.of());
        List<Content> content = Collections.singletonList(Content.newBuilder().contentReference(new ContentReference()).build());
        IngressInput ingressInput = new IngressInput("did", sourceInfo, content, OffsetDateTime.now());

        when(flowService.getRunningFlowByName(sourceInfo.getFlow())).thenThrow(new DgsEntityNotFoundException());
        assertThrows(DgsEntityNotFoundException.class, () -> deltaFilesService.ingress(ingressInput));
    }

    @Test
    void getReturnsNullOnMissingDid() {
        assertNull(deltaFilesService.getDeltaFile("nonsense"));
    }

    @Test
    void testSourceMetadataUnion() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", List.of(
                new KeyValue("k1", "1a"),
                new KeyValue("k2", "val2")));
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", List.of(
                new KeyValue("k1", "1b"),
                new KeyValue("k3", "val3")));
        DeltaFile deltaFile3 = Util.buildDeltaFile("3", List.of(
                new KeyValue("k2", "val2"),
                new KeyValue("k3", null),
                new KeyValue("k4", "val4")));

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
                case "k1":
                    assertEquals(2, u.getValues().size());
                    assertTrue(u.getValues().containsAll(List.of("1a", "1b")));
                    foundKey1 = true;
                    break;
                case "k2":
                    assertEquals(1, u.getValues().size());
                    assertTrue(u.getValues().contains("val2"));
                    foundKey2 = true;
                    break;
                case "k3":
                    assertEquals(2, u.getValues().size());
                    List<String> listWithNull = new ArrayList<>();
                    listWithNull.add("val3");
                    listWithNull.add(null);
                    assertTrue(u.getValues().containsAll(listWithNull));
                    foundKey3 = true;
                    break;
                case "k4":
                    assertEquals(1, u.getValues().size());
                    assertTrue(u.getValues().contains("val4"));
                    foundKey4 = true;
                    break;
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
        ContentReference cr1 = new ContentReference("a", "1", "mediaType");
        deltaFile1.getProtocolStack().add(ProtocolLayer.builder().content(List.of(Content.newBuilder().contentReference(cr1).build())).build());
        ContentReference cr2 = new ContentReference("b", "2", "mediaType");
        DeltaFile deltaFile2 = Util.buildDeltaFile("2");
        deltaFile2.getFormattedData().add(FormattedData.newBuilder().contentReference(cr2).build());
        when(deltaFileRepo.findForDelete(any(), any(), anyLong(), any(), any(), anyBoolean(), anyInt())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.delete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", false, 10);

        verify(contentStorageService).deleteAll(contentReferenceCaptor.capture());
        assertEquals(List.of(cr1, cr2), contentReferenceCaptor.getValue());
        verify(deltaFileRepo).saveAll(deltaFileListCaptor.capture());
        assertEquals(List.of(deltaFile1, deltaFile2), deltaFileListCaptor.getValue());
        assertNotNull(deltaFile1.getContentDeleted());
        assertNotNull(deltaFile2.getContentDeleted());
        verify(deltaFileRepo, never()).deleteAll(any());
    }

    @Test
    void testDeleteMetadata() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1");
        ContentReference cr1 = new ContentReference("a", "1", "mediaType");
        deltaFile1.getProtocolStack().add(ProtocolLayer.builder().content(List.of(Content.newBuilder().contentReference(cr1).build())).build());
        ContentReference cr2 = new ContentReference("b", "2", "mediaType");
        DeltaFile deltaFile2 = Util.buildDeltaFile("2");
        deltaFile2.getFormattedData().add(FormattedData.newBuilder().contentReference(cr2).build());
        when(deltaFileRepo.findForDelete(any(), any(), anyLong(), any(), any(), anyBoolean(), anyInt())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.delete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", true, 10);

        verify(contentStorageService).deleteAll(contentReferenceCaptor.capture());
        assertEquals(List.of(cr1, cr2), contentReferenceCaptor.getValue());
        verify(deltaFileRepo, never()).saveAll(any());
        verify(deltaFileRepo).deleteAll(deltaFileListCaptor.capture());
        assertEquals(List.of(deltaFile1, deltaFile2), deltaFileListCaptor.getValue());
    }

    @Test
    void testRequeue_actionFound() {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.getActions().add(Action.newBuilder().name("action").state(ActionState.QUEUED).modified(modified).build());

        ActionConfiguration actionConfiguration = new FormatActionConfiguration();
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
        Mockito.verify(metricRepository).increment(FILES_ERRORED, MetricsUtil.tagsFor(ActionType.UNKNOWN, "action", deltaFile.getSourceInfo().getFlow(), null), 1);
    }

    @Test
    void testCalculateBytes() {
        ContentReference contentReference1 = new ContentReference("uuid1", 0, 500, "did1", "*/*");
        ContentReference contentReference2 = new ContentReference("uuid1", 400, 200, "did1", "*/*");
        ContentReference contentReference3 = new ContentReference("uuid1", 200, 200, "did1", "*/*");
        ContentReference contentReference4 = new ContentReference("uuid2", 5, 200, "did1", "*/*");
        ContentReference contentReference5 = new ContentReference("uuid3", 5, 200, "did2", "*/*");

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .protocolStack(List.of(
                        new ProtocolLayer("action", List.of(
                                new Content("name", Collections.emptyList(), contentReference1),
                                new Content("name2", Collections.emptyList(), contentReference2)), Collections.emptyList()),
                        new ProtocolLayer("action2", List.of(
                                new Content("name3", Collections.emptyList(), contentReference3)), Collections.emptyList())
                ))
                .formattedData(List.of(
                        FormattedData.newBuilder().contentReference(contentReference4).build(),
                        FormattedData.newBuilder().contentReference(contentReference5).build()
                ))
                .did("did1")
                .build();

        DeltaFilesService.calculateTotalBytes(deltaFile);
        assertEquals(800, deltaFile.getTotalBytes());
    }

    @Test
    void testSplitNoChildFlow() {
        IngressFlow flow = new IngressFlow();
        LoadActionConfiguration actionConfig = new LoadActionConfiguration();
        actionConfig.setName("loadAction");
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
                .build();

        deltaFilesService.split(deltaFile, ActionEventInput.newBuilder()
                .action("loadAction")
                .split(List.of(
                        SplitInput.newBuilder().sourceInfo(
                                SourceInfo.builder().flow("bad").build()).build(),
                        SplitInput.newBuilder().sourceInfo(
                                SourceInfo.builder().flow("bad").build()).build()))
                .build());

        assertTrue(deltaFile.hasErroredAction());
        assertTrue(deltaFile.getActions().stream().filter(a -> a.getState()==ActionState.ERROR).map(Action::getErrorCause)
                .allMatch(DeltaFilesService.NO_CHILD_INGRESS_CONFIGURED_CAUSE::equals));
        assertTrue(deltaFile.getActions().stream().filter(a -> a.getState()==ActionState.ERROR).map(Action::getErrorContext)
                .allMatch(ec -> ec.startsWith(DeltaFilesService.NO_CHILD_INGRESS_CONFIGURED_CONTEXT)));
    }

    @Test
    void testSplitCorrectChildFlow() {
        IngressFlow flow = new IngressFlow();
        LoadActionConfiguration actionConfig = new LoadActionConfiguration();
        actionConfig.setName("loadAction");
        flow.setName("loadAction");
        flow.setLoadAction(actionConfig);

        // "good" flow is available
        when(flowService.getRunningFlowByName(eq("good"))).thenReturn(flow);
        // "bad" flow is not available

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .sourceInfo(SourceInfo.builder().flow("good").build())
                .actions(new ArrayList<>(List.of(Action.newBuilder()
                        .name("reinject").state(ActionState.COMPLETE).build())))
                .did("00000000-0000-0000-00000-000000000000")
                .build();

        deltaFilesService.split(deltaFile, ActionEventInput.newBuilder()
                .action("loadAction")
                .split(List.of(
                        SplitInput.newBuilder().sourceInfo(
                                        SourceInfo.builder().flow("good").build())
                                .content(List.of(Content.newBuilder().contentReference(createContentReference(32L, "first", null, 0L, null))
                                        .build())).build(),
                        SplitInput.newBuilder().sourceInfo(
                                        SourceInfo.builder().flow("good").build())
                                .content(List.of(Content.newBuilder().contentReference(createContentReference(32L, "second", null, 0L, null))
                                        .build())).build()))
                .build());

        assertFalse(deltaFile.hasErroredAction());
        assertTrue(deltaFile.getActions().stream().noneMatch(a -> a.getState()==ActionState.ERROR));
    }

    private ContentReference createContentReference(long size, String did, String uuid, long offset, String mediaType) {
        ContentReference contentReference = new ContentReference();

        contentReference.setSize(size);
        contentReference.setDid(did);
        contentReference.setUuid(uuid);
        contentReference.setOffset(offset);
        contentReference.setMediaType(mediaType);

        return contentReference;
    }
}
