package org.deltafi.core.domain.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.api.types.Content;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.delete.DeleteConstants;
import org.deltafi.core.domain.generated.types.IngressInput;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.deltafi.core.domain.types.IngressFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeltaFilesServiceTest {
    @Mock
    IngressFlowService flowService;

    @Mock
    DeltaFileRepo deltaFileRepo;

    @Mock
    RedisService redisService;

    @SuppressWarnings("unused")
    @Spy
    DeltaFiProperties deltaFiProperties = new DeltaFiProperties();

    @SuppressWarnings("unused")
    @Mock
    ZipkinService zipkinService;

    @SuppressWarnings("unused")
    @Mock
    StateMachine stateMachine;

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
    void testMarkForDelete() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1");
        DeltaFile deltaFile2 = Util.buildDeltaFile("2");
        when(deltaFileRepo.markForDelete(any(), any(), any(), any())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.markForDelete(OffsetDateTime.now().plusSeconds(1), null, null, "policy");

        ArgumentCaptor<List<ActionInput>> deltaFileArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisService).enqueue(deltaFileArgumentCaptor.capture());
        Util.assertEqualsIgnoringDates(deltaFile1.forQueue(DeleteConstants.DELETE_ACTION), deltaFileArgumentCaptor.getValue().get(0).getDeltaFile());
        Util.assertEqualsIgnoringDates(deltaFile2.forQueue(DeleteConstants.DELETE_ACTION), deltaFileArgumentCaptor.getValue().get(1).getDeltaFile());
    }

}
