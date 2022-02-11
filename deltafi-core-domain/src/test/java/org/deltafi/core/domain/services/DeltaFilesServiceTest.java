package org.deltafi.core.domain.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.configuration.IngressFlowConfiguration;
import org.deltafi.core.domain.exceptions.ActionConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeltaFilesServiceTest {
    @Mock
    DeltaFiConfigService deltaFiConfigService;

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
    @InjectMocks
    StateMachine stateMachine = Mockito.spy(new StateMachine());

    @InjectMocks
    DeltaFilesService deltaFilesService;

    @Test
    void setsAndGets() {
        final String flow = "theFlow";
        when(deltaFiConfigService.getIngressFlow(flow)).thenReturn(Optional.of(new IngressFlowConfiguration()));
        String did = UUID.randomUUID().toString();
        SourceInfo sourceInfo = new SourceInfo(null, flow, List.of());
        ContentReference contentReference = new ContentReference();
        IngressInput ingressInput = new IngressInput(did, sourceInfo, contentReference, OffsetDateTime.now());

        DeltaFile deltaFile = deltaFilesService.ingress(ingressInput);

        assertNotNull(deltaFile);
        assertEquals(flow, deltaFile.getSourceInfo().getFlow());
        assertEquals(did, deltaFile.getDid());
        assertNotNull(deltaFile.getProtocolStack().get(0));
    }

    @Test
    void setThrowsOnMissingFlow() {
        SourceInfo sourceInfo = new SourceInfo(null, "nonsense", List.of());
        ContentReference contentReference = new ContentReference();
        IngressInput ingressInput = new IngressInput("did", sourceInfo, contentReference, OffsetDateTime.now());

        assertThrows(DgsEntityNotFoundException.class, () -> deltaFilesService.ingress(ingressInput));
    }

    @Test
    void getReturnsNullOnMissingDid() {
        assertNull(deltaFilesService.getDeltaFile("nonsense"));
    }

    @Test
    void testMarkForDelete() throws ActionConfigException {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1");
        DeltaFile deltaFile2 = Util.buildDeltaFile("2");
        when(deltaFileRepo.markForDelete(any(), any(), any(), any())).thenReturn(List.of(deltaFile1, deltaFile2));

        deltaFilesService.markForDelete(OffsetDateTime.now().plusSeconds(1), null, null, "policy");

        ArgumentCaptor<DeltaFile> deltaFileArgumentCaptor = ArgumentCaptor.forClass(DeltaFile.class);
        verify(redisService, times(2)).enqueue(eq(Collections.singletonList("DeleteAction")), deltaFileArgumentCaptor.capture());
        assertEquals(deltaFileArgumentCaptor.getAllValues().get(0), deltaFile1);
        assertEquals(deltaFileArgumentCaptor.getAllValues().get(1), deltaFile2);
    }
}
