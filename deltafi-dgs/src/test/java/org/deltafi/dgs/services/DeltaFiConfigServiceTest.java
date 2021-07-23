package org.deltafi.dgs.services;

import org.deltafi.dgs.api.types.ConfigType;
import org.deltafi.dgs.configuration.LoadActionGroupConfiguration;
import org.deltafi.dgs.generated.types.ConfigQueryInput;
import org.deltafi.dgs.repo.DeltaFiConfigRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class DeltaFiConfigServiceTest {

    @InjectMocks
    private DeltaFiConfigService configService;

    @Mock
    private DeltaFiConfigRepo deltaFiConfigRepo;

    @Test
    void getIngressFlow() {
        configService.getIngressFlow("flow");
        Mockito.verify(deltaFiConfigRepo).findIngressFlowConfig("flow");
    }

    @Test
    void getEgressFlows() {
        configService.getEgressFlows();
        Mockito.verify(deltaFiConfigRepo).findAllEgressFlows();
    }

    @Test
    void getEgressFlow() {
        configService.getEgressFlow("flow");
        Mockito.verify(deltaFiConfigRepo).findEgressFlowConfig("flow");
    }

    @Test
    void getEgressFlowForAction() {
        configService.getEgressFlowByEgressActionName("FlowEgressAction");
        Mockito.verify(deltaFiConfigRepo).findEgressFlowByEgressActionName("FlowEgressAction");
    }

    @Test
    void getLoadGroupActions() {
        LoadActionGroupConfiguration config = new LoadActionGroupConfiguration();
        config.setName("loadGroup");
        List<String> actions = List.of("a");
        config.setLoadActions(actions);
        Mockito.when(deltaFiConfigRepo.findLoadActionGroup("loadGroup")).thenReturn(config);
        List<String> loadGroupActions = configService.getLoadGroupActions("loadGroup");
        assertEquals(actions, loadGroupActions);
    }

    @Test
    void getConfigs_all() {
        configService.getConfigs(null);
        Mockito.verify(deltaFiConfigRepo).findAll();
    }

    @Test
    void getConfigs_byType() {
        ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(org.deltafi.dgs.generated.types.ConfigType.EGRESS_FLOW).build();
        configService.getConfigs(input);
        Mockito.verify(deltaFiConfigRepo).findAllByConfigType(ConfigType.EGRESS_FLOW);
    }

    @Test
    void getConfigs_byNameAndType() {
        ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(org.deltafi.dgs.generated.types.ConfigType.EGRESS_FLOW).name("action").build();
        configService.getConfigs(input);
        Mockito.verify(deltaFiConfigRepo).findByNameAndConfigType("action", ConfigType.EGRESS_FLOW);
    }

    @Test
    void removeConfigs_all() {
        configService.removeDeltafiConfigs(null);
        Mockito.verify(deltaFiConfigRepo).deleteAllWithCount();
    }

    @Test
    void removeConfigs_byType() {
        ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(org.deltafi.dgs.generated.types.ConfigType.EGRESS_FLOW).build();
        configService.removeDeltafiConfigs(input);
        Mockito.verify(deltaFiConfigRepo).deleteAllByConfigType(ConfigType.EGRESS_FLOW);
    }

    @Test
    void removeConfigs_byNameAndType() {
        ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(org.deltafi.dgs.generated.types.ConfigType.EGRESS_FLOW).name("action").build();
        configService.removeDeltafiConfigs(input);
        Mockito.verify(deltaFiConfigRepo).deleteByNameAndConfigType("action", ConfigType.EGRESS_FLOW);
    }
}