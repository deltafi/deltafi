package org.deltafi.core.domain.services;

import org.assertj.core.api.Assertions;
import org.deltafi.core.domain.configuration.*;
import org.deltafi.core.domain.exceptions.DeltafiConfigurationException;
import org.deltafi.core.domain.generated.types.ConfigQueryInput;
import org.deltafi.core.domain.generated.types.ConfigType;
import org.deltafi.core.domain.generated.types.EgressFlowConfigurationInput;
import org.deltafi.core.domain.repo.DeltaFiRuntimeConfigRepo;
import org.deltafi.core.domain.validation.DeltafiRuntimeConfigurationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Supplier;

@ExtendWith(MockitoExtension.class)
class DeltaFiConfigServiceTest {

    public static final String ACTION_TO_FIND = "SampleLoadAction";
    private static final LoadActionConfiguration action = new LoadActionConfiguration();

    @InjectMocks
    private DeltaFiConfigService configService;

    @Mock
    private DeltaFiRuntimeConfigRepo deltaFiConfigRepo;

    @Mock
    private DeltafiRuntimeConfigurationValidator configValidator;

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
    void testUpdateConfig_invalidChange() {
        EgressFlowConfigurationInput egressFlow = new EgressFlowConfigurationInput();
        egressFlow.setFormatAction("formatAction");
        egressFlow.setName("myFlow");
        egressFlow.setApiVersion("v1");

        Mockito.when(deltaFiConfigRepo.findById(DeltafiRuntimeConfiguration.ID_CONSTANT)).thenReturn(Optional.of(config));
        Mockito.when(configValidator.validate(Mockito.any())).thenReturn(List.of("Failed validation"));

        Assertions.assertThatThrownBy(()-> {
            configService.saveEgressFlow(egressFlow);
        }).isInstanceOf(DeltafiConfigurationException.class)
                .hasMessage("Failed validation");

        Mockito.verify(deltaFiConfigRepo, Mockito.times(0)).save(Mockito.any());

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
        Assertions.assertThat(configs.size()).isEqualTo(10);
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

        Assertions.assertThat(removed).isEqualTo(10);
        Mockito.verify(deltaFiConfigRepo).save(Mockito.any());
    }

    @Test
    void replaceConfig() throws IOException {
        Mockito.when(deltaFiConfigRepo.save(Mockito.any())).thenAnswer(invocation -> invocation.getArguments()[0]);
        String yaml = new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("config-test/load.yaml")).readAllBytes());
        configService.replaceConfig(yaml);
        DeltafiRuntimeConfiguration config = configService.getConfig();

        IngressFlowConfiguration ingressFlow = commonChecks(config.getIngressFlows(), "sample");
        Assertions.assertThat(ingressFlow.getType()).isEqualTo("json");
        Assertions.assertThat(ingressFlow.getTransformActions()).hasSize(1);
        Assertions.assertThat(ingressFlow.getTransformActions()).contains("SampleTransformAction");
        Assertions.assertThat(ingressFlow.getLoadActions()).hasSize(2);
        Assertions.assertThat(ingressFlow.getLoadActions()).contains("SampleLoadAction");
        Assertions.assertThat(ingressFlow.getLoadActions()).contains("Sample2LoadAction");

        EgressFlowConfiguration egressFlowConfiguration = commonChecks(config.getEgressFlows(), "sample");
        Assertions.assertThat(egressFlowConfiguration.getEgressAction()).contains("SampleEgressAction");
        Assertions.assertThat(egressFlowConfiguration.getFormatAction()).isEqualTo("SampleFormatAction");
        Assertions.assertThat(egressFlowConfiguration.getEnrichActions()).hasSize(1);
        Assertions.assertThat(egressFlowConfiguration.getEnrichActions()).contains("SampleEnrichAction");
        Assertions.assertThat(egressFlowConfiguration.getValidateActions()).hasSize(1);
        Assertions.assertThat(egressFlowConfiguration.getValidateActions()).contains("SampleValidateAction");

        TransformActionConfiguration transformActionConfiguration = commonChecks(config.getTransformActions(), "SampleTransformAction");
        Assertions.assertThat(transformActionConfiguration.getName()).isEqualTo("SampleTransformAction");
        Assertions.assertThat(transformActionConfiguration.getConsumes()).isEqualTo("json-utf8");
        Assertions.assertThat(transformActionConfiguration.getProduces()).isEqualTo("json-utf8-sample");

        LoadActionConfiguration loadActionConfiguration = commonChecks(config.getLoadActions(), "SampleLoadAction");
        Assertions.assertThat(loadActionConfiguration.getConsumes()).isEqualTo("json-utf8-sample");
        Assertions.assertThat(loadActionConfiguration.getRequiresMetadata()).hasSize(1);
        Assertions.assertThat(loadActionConfiguration.getRequiresMetadata()).containsEntry("sampleType", "sample-type");

        FormatActionConfiguration formatActionConfiguration = commonChecks(config.getFormatActions(), "SampleFormatAction");
        Assertions.assertThat(formatActionConfiguration.getRequiresDomains()).hasSize(1);
        Assertions.assertThat(formatActionConfiguration.getRequiresDomains()).contains("sample");
        Assertions.assertThat(formatActionConfiguration.getRequiresEnrichment()).hasSize(1);
        Assertions.assertThat(formatActionConfiguration.getRequiresEnrichment()).contains("sampleEnrichment");
        Assertions.assertThat(formatActionConfiguration.getCreated()).isNotNull();
        Assertions.assertThat(formatActionConfiguration.getModified()).isNotNull();

        ValidateActionConfiguration validateActionConfiguration = commonChecks(config.getValidateActions(), "SampleValidateAction");
        Assertions.assertThat(validateActionConfiguration.getName()).isEqualTo("SampleValidateAction");
        Assertions.assertThat(validateActionConfiguration.getType()).isEqualTo("org.deltafi.stix.actions.RubberStampValidateAction");

        EgressActionConfiguration egressActionConfiguration = commonChecks(config.getEgressActions(), "SampleEgressAction");
        Assertions.assertThat(egressActionConfiguration.getType()).isEqualTo("org.deltafi.core.action.RestPostEgressAction");
        Assertions.assertThat(egressActionConfiguration.getParameters()).containsEntry("url", "http://localhost:8085/echo");
        Assertions.assertThat(egressActionConfiguration.getParameters()).containsEntry("metadataKey", "deltafiMetadata");

        DeleteActionConfiguration deleteActionConfiguration = commonChecks(config.getDeleteActions(), "DeleteAction");
        Assertions.assertThat(deleteActionConfiguration.getType()).isEqualTo("org.deltafi.core.action.delete.DeleteAction");
    }

    <C extends DeltaFiConfiguration> C commonChecks(Map<String, C> configs, String name) {
        Assertions.assertThat(configs).hasSize(1);
        Assertions.assertThat(configs).containsKey(name);
        C entry = configs.get(name);
        Assertions.assertThat(entry.getName()).isEqualTo(name);
        Assertions.assertThat(entry.getCreated()).isNotNull();
        Assertions.assertThat(entry.getModified()).isNotNull();
        return entry;
    }

    @Test
    void mergeConfig() throws IOException {
        Mockito.when(deltaFiConfigRepo.save(Mockito.any())).thenAnswer(invocation -> invocation.getArguments()[0]);
        Mockito.when(deltaFiConfigRepo.findById(DeltafiRuntimeConfiguration.ID_CONSTANT)).thenReturn(Optional.of(config));
        String yaml = new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("config-test/load.yaml")).readAllBytes());

        Assertions.assertThat(configService.getConfig().allConfigs()).hasSize(10);
        LoadActionConfiguration preUpdate = configService.getConfig().getLoadActions().get(ACTION_TO_FIND);
        Assertions.assertThat(preUpdate.getConsumes()).isNull();

        configService.mergeConfig(yaml);

        // SampleLoadAction already existed, so it will be replaced and 8 new entries from the config are added
        Assertions.assertThat(configService.getConfig().allConfigs()).hasSize(18);
        LoadActionConfiguration afterUpdate = configService.getConfig().getLoadActions().get(ACTION_TO_FIND);
        Assertions.assertThat(afterUpdate.getConsumes()).isEqualTo("json-utf8-sample");
        Assertions.assertThat(afterUpdate.getCreated()).isEqualTo(preUpdate.getCreated());
        Assertions.assertThat(afterUpdate.getModified()).isAfter(preUpdate.getModified());
        Assertions.assertThat(configService.getConfig().getDeleteActions()).containsKey("DeleteAction");
    }

    DeltafiRuntimeConfiguration buildConfig() {
        DeltafiRuntimeConfiguration newConfig = new DeltafiRuntimeConfiguration();

        action.setName(ACTION_TO_FIND);
        action.setCreated(OffsetDateTime.now().minusHours(1));
        action.setModified(OffsetDateTime.now().minusHours(1));
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

        return newConfig;
    }

    <C extends DeltaFiConfiguration> void createAndAdd(Supplier<C> constructor, String name, Map<String, C> configs) {
        C configItem = constructor.get();
        configItem.setName(name);
        configs.put(name, configItem);
    }

}
