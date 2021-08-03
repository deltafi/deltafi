package org.deltafi.dgs.services;

import org.assertj.core.api.Assertions;
import org.deltafi.dgs.configuration.*;
import org.deltafi.dgs.generated.types.ConfigQueryInput;
import org.deltafi.dgs.generated.types.ConfigType;
import org.deltafi.dgs.generated.types.EgressFlowConfigurationInput;
import org.deltafi.dgs.k8s.GatewayConfigService;
import org.deltafi.dgs.repo.DeltaFiRuntimeConfigRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@ExtendWith(MockitoExtension.class)
class DeltaFiConfigServiceTest {

    public static final String ACTION_TO_FIND = "SampleLoadAction";
    private static final LoadActionConfiguration action = new LoadActionConfiguration();

    @InjectMocks
    private DeltaFiConfigService configService;

    @Mock
    private DeltaFiRuntimeConfigRepo deltaFiConfigRepo;

    @SuppressWarnings("unused")
    @Mock
    ObjectProvider<GatewayConfigService> gatewayConfigService;

    private DeltafiRuntimeConfiguration config;

    @BeforeEach
    void loadConfig() {
        config = buildConfig();
        configService.setConfig(config);
    }

    @Test
    void testGetConfigFromMap_cacheHit() {
        LoadActionConfiguration found = configService.getLoadAction(ACTION_TO_FIND);

        Mockito.verifyNoInteractions(deltaFiConfigRepo);
        Assertions.assertThat(found).isEqualTo(action);
    }

    @Test
    void testGetConfigFromMap_cacheMiss() {
        DeltafiRuntimeConfiguration missingConfig = new DeltafiRuntimeConfiguration();
        configService.setConfig(missingConfig);

        DeltafiRuntimeConfiguration refreshedConfig = new DeltafiRuntimeConfiguration();
        LoadActionConfiguration loadActionConfiguration = new LoadActionConfiguration();
        loadActionConfiguration.setName(ACTION_TO_FIND);
        refreshedConfig.getLoadActions().put(ACTION_TO_FIND, loadActionConfiguration);

        Mockito.when(deltaFiConfigRepo.findById(DeltafiRuntimeConfiguration.ID_CONSTANT)).thenReturn(Optional.of(refreshedConfig));

        LoadActionConfiguration found = configService.getLoadAction(ACTION_TO_FIND);
        Mockito.verify(deltaFiConfigRepo).findById(DeltafiRuntimeConfiguration.ID_CONSTANT);
        Assertions.assertThat(found).isEqualTo(loadActionConfiguration);
    }

    @Test
    void testUpdateConfig() {
        Mockito.when(deltaFiConfigRepo.findById(DeltafiRuntimeConfiguration.ID_CONSTANT)).thenReturn(Optional.of(config));
        DeltafiRuntimeConfiguration updatedConfig = new DeltafiRuntimeConfiguration();
        EgressFlowConfiguration mocked = new EgressFlowConfiguration();
        mocked.setName("myFlow");
        updatedConfig.getEgressFlows().put("myFlow", mocked);
        Mockito.when(deltaFiConfigRepo.save(Mockito.any())).thenReturn(updatedConfig);

        EgressFlowConfigurationInput egressFlow = new EgressFlowConfigurationInput();
        egressFlow.setFormatAction("formatAction");
        egressFlow.setName("myFlow");
        egressFlow.setApiVersion("v1");

        EgressFlowConfiguration saved = configService.saveEgressFlow(egressFlow);

        Mockito.verify(deltaFiConfigRepo).findById(DeltafiRuntimeConfiguration.ID_CONSTANT);
        Mockito.verify(deltaFiConfigRepo).save(Mockito.argThat(config -> config.getEgressFlows().size() == 2));

        Assertions.assertThat(saved.getName()).isEqualTo("myFlow");
        Assertions.assertThat(saved.getEgressAction()).isEqualTo("MyFlowEgressAction");
        Assertions.assertThat(saved.getApiVersion()).isEqualTo("v1");
        Assertions.assertThat(saved.getCreated()).isNotNull();
        Assertions.assertThat(saved.getModified()).isNotNull();

        Assertions.assertThat(configService.getEgressFlow("myFlow")).contains(mocked);
    }

    @Test
    void testGetConfigs_TypeAndName() {
        ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(ConfigType.LOAD_ACTION).name(ACTION_TO_FIND).build();
        List<DeltaFiConfiguration> configs = configService.getConfigs(input);

        Assertions.assertThat(configs.get(0)).isEqualTo(action);
    }

    @Test
    void testGetConfigs_Type() {
        ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(ConfigType.LOAD_ACTION).build();
        List<DeltaFiConfiguration> configs = configService.getConfigs(input);

        Assertions.assertThat(configs.size()).isEqualTo(2);
    }

    @Test
    void testGetConfigs_All() {
        List<DeltaFiConfiguration> configs = configService.getConfigs(null);
        Assertions.assertThat(configs.size()).isEqualTo(11);
    }

    @Test
    void testRemoveConfigs_TypeAndName() {
        Mockito.when(deltaFiConfigRepo.findById(DeltafiRuntimeConfiguration.ID_CONSTANT)).thenReturn(Optional.of(config));
        ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(ConfigType.LOAD_ACTION).name(ACTION_TO_FIND).build();
        long removed = configService.removeDeltafiConfigs(input);

        Assertions.assertThat(removed).isEqualTo(1);
        Mockito.verify(deltaFiConfigRepo).save(Mockito.any());
    }

    @Test
    void testRemoveConfigs_Type() {
        Mockito.when(deltaFiConfigRepo.findById(DeltafiRuntimeConfiguration.ID_CONSTANT)).thenReturn(Optional.of(config));
        ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(ConfigType.LOAD_ACTION).build();
        long removed = configService.removeDeltafiConfigs(input);

        Assertions.assertThat(removed).isEqualTo(2);
        Mockito.verify(deltaFiConfigRepo).save(Mockito.any());
    }

    @Test
    void testRemoveConfigs_All() {
        Mockito.when(deltaFiConfigRepo.findById(DeltafiRuntimeConfiguration.ID_CONSTANT)).thenReturn(Optional.of(config));
        long removed = configService.removeDeltafiConfigs(null);

        Assertions.assertThat(removed).isEqualTo(11);
        Mockito.verify(deltaFiConfigRepo).save(Mockito.any());
    }

    DeltafiRuntimeConfiguration buildConfig() {
        DeltafiRuntimeConfiguration newConfig = new DeltafiRuntimeConfiguration();

        action.setName(ACTION_TO_FIND);
        newConfig.getLoadActions().put(ACTION_TO_FIND, action);
        createAndAdd(TransformActionConfiguration::new, "load", newConfig.getTransformActions());
        createAndAdd(TransformActionConfiguration::new, "transform", newConfig.getTransformActions());
        createAndAdd(LoadActionConfiguration::new, "load", newConfig.getLoadActions());
        createAndAdd(EnrichActionConfiguration::new, "enrich", newConfig.getEnrichActions());
        createAndAdd(FormatActionConfiguration::new, "format", newConfig.getFormatActions());
        createAndAdd(ValidateActionConfiguration::new, "validate", newConfig.getValidateActions());
        createAndAdd(EgressActionConfiguration::new, "egress", newConfig.getEgressActions());
        createAndAdd(IngressFlowConfiguration::new, "ingressFlow", newConfig.getIngressFlows());
        createAndAdd(EgressFlowConfiguration::new, "egressFlow", newConfig.getEgressFlows());
        createAndAdd(DomainEndpointConfiguration::new, "domain", newConfig.getDomainEndpoints());

        return newConfig;
    }

    <C extends DeltaFiConfiguration> void createAndAdd(Supplier<C> constructor, String name, Map<String, C> configs) {
        C configItem = constructor.get();
        configItem.setName(name);
        configs.put(name, configItem);
    }
}