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
import org.deltafi.dgs.repo.ErrorRepo;
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
    final ActionConfigService actionConfigService = Mockito.mock(ActionConfigService.class);
    StateMachine stateMachine;
    DeltaFileRepo deltaFileRepo;
    ErrorRepo errorRepo;
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
        Mockito.when(deltaFiConfigService.getEgressFlowByEgressActionName(EgressConfiguration.egressActionName(flow1))).thenReturn(flowConfiguration1);

        ZipkinConfig zipkinConfig = new ZipkinConfig();
        zipkinConfig.setEnabled(false);
        stateMachine = new StateMachine(deltaFiConfigService, actionConfigService, new ZipkinService(null, zipkinConfig));

        deltaFileRepo = Mockito.mock(DeltaFileRepo.class);
        errorRepo = Mockito.mock(ErrorRepo.class);
        deltaFilesService = new DeltaFilesService(deltaFiConfigService, new DeltaFiProperties(), stateMachine, deltaFileRepo, errorRepo, redisService);
    }

    @Test
    void setsAndGets() {
        Mockito.when(deltaFileRepo.save(Mockito.any(DeltaFile.class))).thenAnswer((i) -> i.getArguments()[0]);
        String did = UUID.randomUUID().toString();
        SourceInfoInput sourceInfoInput = SourceInfoInput.newBuilder().flow(flow).build();
        ObjectReferenceInput objectReferenceInput = new ObjectReferenceInput();
        IngressInput ingressInput = new IngressInput(sourceInfoInput, objectReferenceInput, OffsetDateTime.now());
        ActionEventInput event = ActionEventInput.newBuilder()
                .did(did)
                .type(ActionEventType.INGRESS)
                .time(OffsetDateTime.now())
                .action("IngressAction")
                .ingress(ingressInput)
                .build();
        DeltaFile deltaFile = deltaFilesService.addDeltaFile(event);

        assertNotNull(deltaFile);
        assertEquals(flow, deltaFile.getSourceInfo().getFlow());
        assertEquals(did, deltaFile.getDid());
        assertNotNull(deltaFile.getProtocolStack().get(0));
    }

    @Test
    void setThrowsOnMissingFlow() {
        SourceInfoInput sourceInfoInput = SourceInfoInput.newBuilder().flow("nonsense").build();
        ObjectReferenceInput objectReferenceInput = new ObjectReferenceInput();
        IngressInput ingressInput = new IngressInput(sourceInfoInput, objectReferenceInput, OffsetDateTime.now());
        ActionEventInput event = ActionEventInput.newBuilder()
                .did("did")
                .type(ActionEventType.INGRESS)
                .time(OffsetDateTime.now())
                .action("IngressAction")
                .ingress(ingressInput)
                .build();
        assertThrows(DgsEntityNotFoundException.class, () -> deltaFilesService.addDeltaFile(event));
    }

    @Test
    void getReturnsNullOnMissingDid() {
        assertNull(deltaFilesService.getDeltaFile("nonsense"));
    }
}