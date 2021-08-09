package org.deltafi.dgs.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.common.trace.ZipkinConfig;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.dgs.api.repo.DeltaFileRepo;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.configuration.EgressFlowConfiguration;
import org.deltafi.dgs.configuration.IngressFlowConfiguration;
import org.deltafi.dgs.generated.types.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeltaFilesServiceTest {
    DeltaFilesService deltaFilesService;
    final DeltaFiConfigService deltaFiConfigService = Mockito.mock(DeltaFiConfigService.class);
    StateMachine stateMachine;
    DeltaFileRepo deltaFileRepo;
    final RedisService redisService = Mockito.mock(RedisService.class);

    final String flow = "theFlow";

    final String flow1 = "flow1";
    final String formatAction1 = "1FormatAction";
    final String flow2 = "flow2";
    final String formatAction2 = "2FormatAction";

    @BeforeEach
    void setup() {
        IngressFlowConfiguration flowConfiguration = new IngressFlowConfiguration();
        Mockito.when(deltaFiConfigService.getIngressFlow(flow)).thenReturn(Optional.of(flowConfiguration));

        EgressFlowConfiguration flowConfiguration1 = new EgressFlowConfiguration();
        flowConfiguration1.setFormatAction(formatAction1);
        EgressFlowConfiguration flowConfiguration2 = new EgressFlowConfiguration();
        flowConfiguration2.setFormatAction(formatAction2);

        Mockito.when(deltaFiConfigService.getEgressFlow(flow1)).thenReturn(Optional.of(flowConfiguration1));
        Mockito.when(deltaFiConfigService.getEgressFlow(flow2)).thenReturn(Optional.of(flowConfiguration2));
        Mockito.when(deltaFiConfigService.getEgressFlows()).thenReturn(Arrays.asList(flowConfiguration1, flowConfiguration2));

        ZipkinConfig zipkinConfig = new ZipkinConfig();
        zipkinConfig.setEnabled(false);
        stateMachine = new StateMachine(deltaFiConfigService, new ZipkinService(null, zipkinConfig));

        deltaFileRepo = Mockito.mock(DeltaFileRepo.class);
        deltaFilesService = new DeltaFilesService(deltaFiConfigService, new DeltaFiProperties(), stateMachine, deltaFileRepo, redisService);
    }

    @Test
    void setsAndGets() {
        Mockito.when(deltaFileRepo.save(Mockito.any(DeltaFile.class))).thenAnswer((i) -> i.getArguments()[0]);
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
}