package org.deltafi.core.domain.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.core.domain.generated.types.IngressInput;
import org.deltafi.core.domain.generated.types.ObjectReferenceInput;
import org.deltafi.core.domain.generated.types.SourceInfoInput;
import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.api.repo.DeltaFileRepo;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.configuration.IngressFlowConfiguration;
import org.deltafi.core.domain.exceptions.ActionConfigException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = "enableScheduling=false")
class DeltaFilesServiceTest {
    @MockBean
    DeltaFiConfigService deltaFiConfigService;

    @MockBean
    DeltaFileRepo deltaFileRepo;

    @MockBean
    RedisService redisService;

    @Autowired
    DeltaFilesService deltaFilesService;

    @Test
    void setsAndGets() {
        final String flow = "theFlow";
        when(deltaFiConfigService.getIngressFlow(flow)).thenReturn(Optional.of(new IngressFlowConfiguration()));
        String did = UUID.randomUUID().toString();
        SourceInfoInput sourceInfoInput = SourceInfoInput.newBuilder().flow(flow).build();
        ObjectReferenceInput objectReferenceInput = new ObjectReferenceInput();
        IngressInput ingressInput = new IngressInput(did, sourceInfoInput, objectReferenceInput, OffsetDateTime.now());

        DeltaFile deltaFile = deltaFilesService.addDeltaFile(ingressInput);

        assertNotNull(deltaFile);
        assertEquals(flow, deltaFile.getSourceInfo().getFlow());
        assertEquals(did, deltaFile.getDid());
        assertNotNull(deltaFile.getProtocolStack().get(0));
    }

    @Test
    void setThrowsOnMissingFlow() {
        SourceInfoInput sourceInfoInput = SourceInfoInput.newBuilder().flow("nonsense").build();
        ObjectReferenceInput objectReferenceInput = new ObjectReferenceInput();
        IngressInput ingressInput = new IngressInput("did", sourceInfoInput, objectReferenceInput, OffsetDateTime.now());

        assertThrows(DgsEntityNotFoundException.class, () -> deltaFilesService.addDeltaFile(ingressInput));
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