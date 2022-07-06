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
package org.deltafi.core.domain.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.assertj.core.api.Assertions;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.configuration.FormatActionConfiguration;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.deltafi.core.domain.types.IngressFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeltaFilesServiceTest {
    @Mock
    IngressFlowService flowService;

    @Mock
    FlowAssignmentService flowAssignmentService;

    @Mock
    DeltaFileRepo deltaFileRepo;

    @Mock
    ContentStorageService contentStorageService;

    @Captor
    ArgumentCaptor<List<String>> stringListCaptor;

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

    @InjectMocks
    DeltaFilesService deltaFilesService;

    @Test
    void setsAndGets() {
        final String flow = "theFlow";
        SourceInfo sourceInfo = new SourceInfo(null, flow, List.of());

        when(flowService.getRunningFlowByName(sourceInfo.getFlow())).thenReturn(new IngressFlow());
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

        when(deltaFileRepo.findById("1")).thenReturn(Optional.of(deltaFile1));
        when(deltaFileRepo.findById("2")).thenReturn(Optional.of(deltaFile2));
        when(deltaFileRepo.findById("3")).thenReturn(Optional.of(deltaFile3));
        when(deltaFileRepo.findById("4")).thenReturn(Optional.empty());

        List<UniqueKeyValues> uniqueMetadata = deltaFilesService.sourceMetadataUnion(List.of("1", "2", "3", "4"));
        assertEquals(4, uniqueMetadata.size());

        boolean foundKey1 = false;
        boolean foundKey2 = false;
        boolean foundKey3 = false;
        boolean foundKey4 = false;

        for (UniqueKeyValues u : uniqueMetadata) {
            if (u.getKey().equals("k1")) {
                assertEquals(2, u.getValues().size());
                assertTrue(u.getValues().containsAll(List.of("1a", "1b")));
                foundKey1 = true;
            } else if (u.getKey().equals("k2")) {
                assertEquals(1, u.getValues().size());
                assertTrue(u.getValues().contains("val2"));
                foundKey2 = true;
            } else if (u.getKey().equals("k3")) {
                assertEquals(2, u.getValues().size());
                List<String> listWithNull = new ArrayList<>();
                listWithNull.add("val3");
                listWithNull.add(null);
                assertTrue(u.getValues().containsAll(listWithNull));
                foundKey3 = true;
            } else if (u.getKey().equals("k4")) {
                assertEquals(1, u.getValues().size());
                assertTrue(u.getValues().contains("val4"));
                foundKey4 = true;
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
        DeltaFile deltaFile2 = Util.buildDeltaFile("2");
        when(deltaFileRepo.findForDelete(any(), any(), anyLong(), any(), any(), anyBoolean())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.delete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", false);

        verify(contentStorageService).deleteAll(stringListCaptor.capture());
        assertEquals(List.of("1", "2"), stringListCaptor.getValue());
        verify(deltaFileRepo).saveAll(deltaFileListCaptor.capture());
        assertEquals(List.of(deltaFile1, deltaFile2), deltaFileListCaptor.getValue());
        assertNotNull(deltaFile1.getContentDeleted());
        assertNotNull(deltaFile2.getContentDeleted());
        verify(deltaFileRepo, never()).deleteAll(any());
    }

    @Test
    void testDeleteMetadata() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1");
        DeltaFile deltaFile2 = Util.buildDeltaFile("2");
        when(deltaFileRepo.findForDelete(any(), any(), anyLong(), any(), any(), anyBoolean())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.delete(OffsetDateTime.now().plusSeconds(1), null, 0L, null, "policy", true);

        verify(contentStorageService).deleteAll(stringListCaptor.capture());
        assertEquals(List.of("1", "2"), stringListCaptor.getValue());
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
    void testRequeue_actionNotFound() {
        OffsetDateTime modified = OffsetDateTime.now();
        DeltaFile deltaFile = Util.buildDeltaFile("1");
        deltaFile.getActions().add(Action.newBuilder().name("action").state(ActionState.QUEUED).modified(modified).build());

        List<ActionInput> toQueue = deltaFilesService.requeuedActionInput(deltaFile, modified);
        Assertions.assertThat(toQueue).isEmpty();

        ArgumentCaptor<DeltaFile> deltaFileCaptor = ArgumentCaptor.forClass(DeltaFile.class);
        Mockito.verify(stateMachine, times(2)).advance(deltaFileCaptor.capture());

        List<DeltaFile> captured = deltaFileCaptor.getAllValues();

        DeltaFile erroredDeltaFile = captured.get(1);
        Optional<Action> maybeAction = erroredDeltaFile.actionNamed("action");
        Assertions.assertThat(maybeAction).isPresent();
        Action action = maybeAction.get();
        Assertions.assertThat(action.getState()).isEqualTo(ActionState.ERROR);
        Assertions.assertThat(action.getErrorCause()).isEqualTo("Action named action is no longer running");
    }

    @Test
    void testCalculateBytes() {
        ContentReference contentReference1 = new ContentReference("uuid1", 0, 500, "did1", "*/*");
        ContentReference contentReference2 = new ContentReference("uuid1", 400, 200, "did1", "*/*");
        ContentReference contentReference3 = new ContentReference("uuid1", 200, 200, "did1", "*/*");
        ContentReference contentReference4 = new ContentReference("uuid2", 5, 200, "did1", "*/*");

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .protocolStack(List.of(
                        new ProtocolLayer("type", "action", List.of(
                                new Content("name", Collections.emptyList(), contentReference1),
                                new Content("name2", Collections.emptyList(), contentReference2)), Collections.emptyList()),
                        new ProtocolLayer("type2", "action2", List.of(
                                new Content("name3", Collections.emptyList(), contentReference3)), Collections.emptyList())
                ))
                .formattedData(List.of(
                        FormattedData.newBuilder().contentReference(contentReference4).build()
                ))
                .build();

        DeltaFilesService.calculateTotalBytes(deltaFile);
        assertEquals(800, deltaFile.getTotalBytes());
    }
}