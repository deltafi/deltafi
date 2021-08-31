package org.deltafi.dgs.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.dgs.api.types.ConfigType;
import org.deltafi.dgs.configuration.ActionConfiguration;
import org.deltafi.dgs.configuration.DeltaFiConfiguration;
import org.deltafi.dgs.configuration.EgressActionConfiguration;
import org.deltafi.dgs.configuration.EgressFlowConfiguration;
import org.deltafi.dgs.configuration.EnrichActionConfiguration;
import org.deltafi.dgs.configuration.FormatActionConfiguration;
import org.deltafi.dgs.configuration.IngressFlowConfiguration;
import org.deltafi.dgs.configuration.LoadActionConfiguration;
import org.deltafi.dgs.configuration.LoadActionGroupConfiguration;
import org.deltafi.dgs.configuration.TransformActionConfiguration;
import org.deltafi.dgs.configuration.ValidateActionConfiguration;
import org.deltafi.dgs.configuration.*;
import org.deltafi.dgs.converters.KeyValueConverter;
import org.deltafi.dgs.exceptions.ActionConfigException;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.repo.DeltaFiRuntimeConfigRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeltaFiConfigService {

    private static final Logger log = LoggerFactory.getLogger(DeltaFiConfigService.class);
    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    Representer representer = new Representer() {
        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean, Property property,
                                                      Object propertyValue, Tag customTag) {
            if (Objects.isNull(propertyValue) || propertyValue instanceof OffsetDateTime) {
                return null;
            } else {
                return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
            }
        }
    };

    Yaml yaml = new Yaml(new Constructor(DeltafiRuntimeConfiguration.class), representer);

    private final DeltaFiRuntimeConfigRepo configRepo;

    private DeltafiRuntimeConfiguration config;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public DeltaFiConfigService(DeltaFiRuntimeConfigRepo configRepo) {
        this.configRepo = configRepo;
    }

    @PostConstruct
    public void getOrCreateDefaultConfig() {
        this.config = configRepo.findById(DeltafiRuntimeConfiguration.ID_CONSTANT).orElseGet(this::defaultConfig);
    }

    private DeltafiRuntimeConfiguration defaultConfig() {
        log.info("Started with no config loading empty config");
        return configRepo.save(new DeltafiRuntimeConfiguration());
    }

    @Async
    public void refreshConfig() {
        config = getCurrentConfig();
    }

    void setConfig(DeltafiRuntimeConfiguration config) {
        this.config = config;
    }

    DeltafiRuntimeConfiguration getConfig() {
        return config;
    }

    public String exportConfigAsYaml() {
        return yaml.dumpAsMap(config);
    }

    public String replaceConfig(String configInput) {
        DeltafiRuntimeConfiguration incoming = yaml.load(configInput);
        incoming.allConfigs().forEach(this::setTimes);
        incoming.getEgressFlows().values().forEach(this::setEgressActionName);
        config = configRepo.save(incoming);
        return exportConfigAsYaml();
    }

    void setTimes(DeltaFiConfiguration config) {
        OffsetDateTime now = OffsetDateTime.now();
        config.setModified(now);
        config.setCreated(now);
    }

    public String mergeConfig(String configInput) {
        DeltafiRuntimeConfiguration incoming = yaml.load(configInput);
        incoming.getEgressFlows().values().forEach(this::setEgressActionName);
        DeltafiRuntimeConfiguration existing = getCurrentConfig();

        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getTransformActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getLoadActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getEnrichActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getFormatActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getValidateActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getEgressActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getIngressFlows);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getEgressFlows);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getLoadGroups);

        config = configRepo.save(existing);
        return exportConfigAsYaml();
    }

    public void setEgressActionName(EgressFlowConfiguration egressFlowConfiguration) {
        if (Objects.isNull(egressFlowConfiguration.getEgressAction()) || egressFlowConfiguration.getEgressAction().isBlank()) {
            egressFlowConfiguration.setEgressAction(EgressConfiguration.egressActionName(egressFlowConfiguration.getName()));
        }
    }

    public Optional<IngressFlowConfiguration> getIngressFlow(String flow) {
        return Optional.ofNullable(getConfigFromMap(DeltafiRuntimeConfiguration::getIngressFlows, flow));
    }

    public Collection<EgressFlowConfiguration> getEgressFlows() {
        return config.getEgressFlows().values();
    }

    public Optional<EgressFlowConfiguration> getEgressFlow(String flow) {
        return Optional.ofNullable(getConfigFromMap(DeltafiRuntimeConfiguration::getEgressFlows, flow));
    }

    public List<String> getEgressFlowsWithFormatAction(String formatAction) {
        List<String> egressFlows = doGetEgressFlowsWithFormatAction(formatAction);

        if (egressFlows.isEmpty()) {
            refreshConfig();
            egressFlows = doGetEgressFlowsWithFormatAction(formatAction);
        }
        return egressFlows;
    }

    List<String> doGetEgressFlowsWithFormatAction(String formatAction) {
        return config.getEgressFlows().values().stream().filter(egressFlowConfiguration -> formatAction.equals(egressFlowConfiguration.getFormatAction())).map(EgressFlowConfiguration::getEgressAction).collect(Collectors.toList());
    }

    public List<String> getLoadGroupActions(String loadGroupName) {
        LoadActionGroupConfiguration loadActionGroupConfiguration = getConfigFromMap(DeltafiRuntimeConfiguration::getLoadGroups, loadGroupName);
        return Objects.nonNull(loadActionGroupConfiguration) ? loadActionGroupConfiguration.getLoadActions() : Collections.emptyList();
    }

    public ActionConfiguration getConfigForAction(String actionName) throws ActionConfigException {
        Optional<ActionConfiguration> maybeActionConfig = config.findByActionName(actionName);
        if (maybeActionConfig.isEmpty()) {
            refreshConfig();
            return config.findByActionName(actionName).orElseThrow(() -> new ActionConfigException(actionName, "Action Configuration was not found"));
        }

        return maybeActionConfig.get();
    }

    public LoadActionConfiguration getLoadAction(String loadAction) {
        return getConfigFromMap(DeltafiRuntimeConfiguration::getLoadActions, loadAction);
    }

    public EnrichActionConfiguration getEnrichAction(String enrichAction) {
        return getConfigFromMap(DeltafiRuntimeConfiguration::getEnrichActions, enrichAction);
    }

    public FormatActionConfiguration getFormatAction(String formatAction) {
        return getConfigFromMap(DeltafiRuntimeConfiguration::getFormatActions, formatAction);
    }

    public List<String> getEnrichActions() {
        return config.getEnrichActions().values().stream().map(DeltaFiConfiguration::getName).collect(Collectors.toList());
    }

    public List<String> getFormatActions() {
        return config.getFormatActions().values().stream().map(DeltaFiConfiguration::getName).collect(Collectors.toList());
    }

    public List<DeltaFiConfiguration> getConfigs(ConfigQueryInput actionQueryInput) {
        if (Objects.nonNull(actionQueryInput)) {
            ConfigType configType = mapper.convertValue(actionQueryInput.getConfigType(), ConfigType.class);
            if (Objects.nonNull(actionQueryInput.getName())) {
                DeltaFiConfiguration actionConfiguration = config.getMapByType(configType).get(actionQueryInput.getName());
                return Objects.nonNull(actionConfiguration) ? List.of(actionConfiguration) : Collections.emptyList();
            } else {
                return new ArrayList<>(config.getMapByType(configType).values());
            }
        }
        return config.allConfigs();
    }

    public TransformActionConfiguration saveTransformAction(TransformActionConfigurationInput transformActionConfigurationInput) {
        return updateConfig(transformActionConfigurationInput, DeltafiRuntimeConfiguration::getTransformActions, transformActionConfigurationInput.getName(), TransformActionConfiguration.class);
    }

    public LoadActionConfiguration saveLoadAction(LoadActionConfigurationInput loadActionConfigurationInput) {
        Consumer<LoadActionConfiguration> convertKeyValueInputs = c -> c.setRequiresMetadata(KeyValueConverter.convertKeyValueInputs(loadActionConfigurationInput.getRequiresMetadataKeyValues()));
        return updateConfig(loadActionConfigurationInput, DeltafiRuntimeConfiguration::getLoadActions, loadActionConfigurationInput.getName(), convertKeyValueInputs, LoadActionConfiguration.class);
    }

    public EnrichActionConfiguration saveEnrichAction(EnrichActionConfigurationInput enrichActionConfigurationInput) {
        return updateConfig(enrichActionConfigurationInput, DeltafiRuntimeConfiguration::getEnrichActions, enrichActionConfigurationInput.getName(), EnrichActionConfiguration.class);
    }

    public FormatActionConfiguration saveFormatAction(FormatActionConfigurationInput formatActionConfigurationInput) {
        return updateConfig(formatActionConfigurationInput, DeltafiRuntimeConfiguration::getFormatActions, formatActionConfigurationInput.getName(), FormatActionConfiguration.class);
    }

    public ValidateActionConfiguration saveValidateAction(ValidateActionConfigurationInput validateActionConfigurationInput) {
        return updateConfig(validateActionConfigurationInput, DeltafiRuntimeConfiguration::getValidateActions, validateActionConfigurationInput.getName(), ValidateActionConfiguration.class);
    }

    public EgressActionConfiguration saveEgressAction(EgressActionConfigurationInput egressActionConfigurationInput) {
        return updateConfig(egressActionConfigurationInput, DeltafiRuntimeConfiguration::getEgressActions, egressActionConfigurationInput.getName(), EgressActionConfiguration.class);
    }

    public IngressFlowConfiguration saveIngressFlow(IngressFlowConfigurationInput ingressFlowConfigurationInput) {
        return updateConfig(ingressFlowConfigurationInput, DeltafiRuntimeConfiguration::getIngressFlows, ingressFlowConfigurationInput.getName(), IngressFlowConfiguration.class);
    }

    public EgressFlowConfiguration saveEgressFlow(EgressFlowConfigurationInput egressFlowConfigurationInput) {
        Consumer<EgressFlowConfiguration> setEgressAction = egressFlowConfiguration -> egressFlowConfiguration.setEgressAction(EgressConfiguration.egressActionName(egressFlowConfiguration.getName()));
        return updateConfig(egressFlowConfigurationInput, DeltafiRuntimeConfiguration::getEgressFlows, egressFlowConfigurationInput.getName(), setEgressAction, EgressFlowConfiguration.class);
    }

    public LoadActionGroupConfiguration saveLoadActionGroup(LoadActionGroupConfigurationInput loadActionGroup) {
        return updateConfig(loadActionGroup, DeltafiRuntimeConfiguration::getLoadGroups, loadActionGroup.getName(), LoadActionGroupConfiguration.class);
    }

    public long removeDeltafiConfigs(ConfigQueryInput configQuery) {
        if (Objects.nonNull(configQuery)) {
            ConfigType configType = mapper.convertValue(configQuery.getConfigType(), ConfigType.class);
            if (Objects.nonNull(configQuery.getName())) {
                return removeByTypeAndName(configType, configQuery.getName());
            } else {
                return removeAllByType(configType);
            }
        }
        return removeAll();
    }

    private int removeAllByType(ConfigType configType) {
        DeltafiRuntimeConfiguration current = getCurrentConfig();
        Map<String, ? extends DeltaFiConfiguration> toClear = current.getMapByType(configType);
        int removing = toClear.size();
        if (removing > 0) {
            toClear.clear();
            config = configRepo.save(current);
        }
        return removing;
    }

    private int removeByTypeAndName(ConfigType configType, String configName) {
        DeltafiRuntimeConfiguration current = getCurrentConfig();
        Map<String, ? extends DeltaFiConfiguration> toClear = current.getMapByType(configType);
        DeltaFiConfiguration removed = toClear.remove(configName);
        if (Objects.nonNull(removed)) {
            config = configRepo.save(current);
            return 1;
        }
        return 0;
    }

    private int removeAll() {
        DeltafiRuntimeConfiguration current = getCurrentConfig();

        int removed = 0;
        for (ConfigType configType: ConfigType.values()) {
            Map<String, ? extends DeltaFiConfiguration> toClear = current.getMapByType(configType);
            removed += toClear.size();
            toClear.clear();
        }

        config = configRepo.save(current);
        return removed;
    }

    private DeltafiRuntimeConfiguration getCurrentConfig() {
        return configRepo.findById(DeltafiRuntimeConfiguration.ID_CONSTANT).orElseThrow(() -> new IllegalStateException("Could not find the deltafi configuration"));
    }

    public <C extends DeltaFiConfiguration> C getConfigFromMap(Function<DeltafiRuntimeConfiguration, Map<String, C>> mapFunction, String name) {
        C actionConfig = mapFunction.apply(config).get(name);

        if (Objects.isNull(actionConfig)) {
            refreshConfig();
            actionConfig = mapFunction.apply(config).get(name);
        }

        return actionConfig;
    }

    private <C extends DeltaFiConfiguration> C updateConfig(Object input, Function<DeltafiRuntimeConfiguration, Map<String, C>> configMap, String inputName, Class<C> clazz) {
        return updateConfig(input, configMap, inputName, null, clazz);
    }

    private <C extends DeltaFiConfiguration> C updateConfig(Object input, Function<DeltafiRuntimeConfiguration, Map<String, C>> configMap, String inputName, Consumer<C> optionalCallbacks, Class<C> clazz) {
        DeltafiRuntimeConfiguration current = getCurrentConfig();

        C configToSave = mapper.convertValue(input, clazz);

        if (Objects.nonNull(optionalCallbacks)) {
            optionalCallbacks.accept(configToSave);
        }

        configToSave.setModified(OffsetDateTime.now());

        Map<String, C> configs = configMap.apply(current);

        configToSave.setCreated(OffsetDateTime.now());

        if (configs.containsKey(inputName)) {
            configToSave.setCreated(configs.get(inputName).getCreated());
        }

        configs.put(inputName, configToSave);
        config = configRepo.save(current);

        return configToSave;
    }

    public <C extends DeltaFiConfiguration> void mergeMap(DeltafiRuntimeConfiguration incoming, DeltafiRuntimeConfiguration existing, Function<DeltafiRuntimeConfiguration, Map<String, C>> mapFunction) {
        Map<String, C> incomingMap = mapFunction.apply(incoming);
        Map<String, C> existingMap = mapFunction.apply(existing);

        for (Map.Entry<String, C> entry : incomingMap.entrySet()) {
            String key = entry.getKey();
            C configEntry = entry.getValue();

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime created = existingMap.containsKey(key) ? existingMap.get(key).getCreated() : now;
            configEntry.setModified(now);
            configEntry.setCreated(created);

            existingMap.put(key, configEntry);
        }
    }

}
