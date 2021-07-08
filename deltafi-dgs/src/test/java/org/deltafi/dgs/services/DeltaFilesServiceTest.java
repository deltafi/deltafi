package org.deltafi.dgs.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.common.trace.ZipkinConfig;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.*;
import org.deltafi.dgs.configuration.EgressFlowConfiguration;
import org.deltafi.dgs.configuration.IngressFlowConfiguration;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.repo.DeltaFileRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Optional;

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
        Mockito.when(deltaFiConfigService.getEgressFlowForAction(EgressConfiguration.egressActionName(flow1))).thenReturn(flowConfiguration1);

        ZipkinConfig zipkinConfig = new ZipkinConfig();
        zipkinConfig.setEnabled(false);
        stateMachine = new StateMachine(deltaFiConfigService, new ZipkinService(null, zipkinConfig));

        deltaFileRepo = Mockito.mock(DeltaFileRepo.class);
        deltaFilesService = new DeltaFilesService(deltaFiConfigService, new DeltaFiProperties(), stateMachine, deltaFileRepo, redisService);
    }

    @Test
    void setsAndGets() {
        Mockito.when(deltaFileRepo.save(Mockito.any(DeltaFile.class))).thenAnswer((i) -> i.getArguments()[0]);
        SourceInfoInput sourceInfoInput = SourceInfoInput.newBuilder().flow(flow).build();
        ObjectReferenceInput objectReferenceInput = new ObjectReferenceInput();
        DeltaFile deltaFile = deltaFilesService.addDeltaFile(sourceInfoInput, objectReferenceInput);

        assertNotNull(deltaFile);
        assertEquals(flow, sourceInfoInput.getFlow());
        assertNotNull(deltaFile.getProtocolStack().get(0));
    }

    @Test
    void setThrowsOnMissingFlow() {
        SourceInfoInput sourceInfoInput = SourceInfoInput.newBuilder().flow("nonsense").build();
        ObjectReferenceInput objectReferenceInput = new ObjectReferenceInput();
        assertThrows(DgsEntityNotFoundException.class, () -> deltaFilesService.addDeltaFile(sourceInfoInput, objectReferenceInput));
    }

    @Test
    void getReturnsNullOnMissingDid() {
        assertNull(deltaFilesService.getDeltaFile("nonsense"));
    }
}
