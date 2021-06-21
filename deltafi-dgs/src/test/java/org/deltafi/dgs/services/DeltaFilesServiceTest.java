package org.deltafi.dgs.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.dgs.Util;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.*;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.repo.DeltaFileRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class DeltaFilesServiceTest {
    DeltaFilesService deltaFilesService;
    DeltaFiProperties deltaFiProperties;
    StateMachine stateMachine;
    DeltaFileRepo deltaFileRepo;

    final String flow = "theFlow";

    final String flow1 = "flow1";
    final String formatAction1 = "1FormatAction";
    final String flow2 = "flow2";
    final String formatAction2 = "2FormatAction";

    @BeforeEach
    void setup() {
        deltaFiProperties = new DeltaFiProperties();

        IngressConfiguration ingressConfiguration = new IngressConfiguration();
        IngressFlowConfiguration flowConfiguration = new IngressFlowConfiguration();
        ingressConfiguration.getIngressFlows().put(flow, flowConfiguration);
        deltaFiProperties.setIngress(ingressConfiguration);

        EgressConfiguration egressConfiguration = new EgressConfiguration();
        EgressFlowConfiguration flowConfiguration1 = new EgressFlowConfiguration();
        flowConfiguration1.setFormatAction(formatAction1);
        egressConfiguration.getEgressFlows().put(flow1, flowConfiguration1);
        EgressFlowConfiguration flowConfiguration2 = new EgressFlowConfiguration();
        flowConfiguration2.setFormatAction(formatAction2);
        egressConfiguration.getEgressFlows().put(flow2, flowConfiguration2);
        deltaFiProperties.setEgress(egressConfiguration);

        stateMachine = new StateMachine(deltaFiProperties, new ZipkinService(null, false));

        deltaFileRepo = Mockito.mock(DeltaFileRepo.class);
        deltaFilesService = new DeltaFilesService(deltaFiProperties, stateMachine, deltaFileRepo);
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

    @Test
    void returnsOnlyMyFormatData() {
        DeltaFile doubleFormatted = Util.emptyDeltaFile("did", "flow");
        doubleFormatted.getFormattedData().add(FormattedData.newBuilder().formatAction(formatAction1).build());
        doubleFormatted.getFormattedData().add(FormattedData.newBuilder().formatAction(formatAction2).build());
        doubleFormatted.getActions().add(Action.newBuilder().name(EgressConfiguration.egressActionName(flow1)).state(ActionState.QUEUED).build());

        Mockito.when(deltaFileRepo.findAndDispatchForAction(EgressConfiguration.egressActionName(flow1), 12, false)).thenReturn(Collections.singletonList(doubleFormatted));

        List<DeltaFile> results = deltaFilesService.actionFeed(EgressConfiguration.egressActionName(flow1), 12, false);
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).getFormattedData().size()).isEqualTo(1);
        assertThat(results.get(0).getFormattedData().get(0).getFormatAction()).isEqualTo(formatAction1);
    }

}
