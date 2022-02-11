package org.deltafi.core.domain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.core.domain.api.types.ConfigType;
import org.deltafi.core.domain.configuration.*;
import org.deltafi.core.domain.exceptions.ActionConfigException;
import org.deltafi.core.domain.exceptions.DeltafiConfigurationException;
import org.deltafi.core.domain.generated.types.ConfigQueryInput;
import org.deltafi.core.domain.repo.DeltaFiRuntimeConfigRepo;
import org.deltafi.core.domain.validation.DeltafiRuntimeConfigurationValidator;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
public class DeltaFiConfigService {

    private static final Logger log = LoggerFactory.getLogger(DeltaFiConfigService.class);
    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final DeltafiRuntimeConfigurationValidator configurationValidator;
    private final DeltaFiRuntimeConfigRepo configRepo;
    Representer representer = new Representer() {
        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean, Property property,
                                                      Object propertyValue, Tag customTag) {
            if (isNull(propertyValue) || propertyValue instanceof OffsetDateTime) {
                return null;
            } else if (property.getName().equals("name")) {
                return null;
            } else {
                return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
            }
        }
    };
    Yaml yaml = new Yaml(new Constructor(DeltafiRuntimeConfiguration.class), representer);
    private DeltafiRuntimeConfiguration config;

    public DeltaFiConfigService(DeltaFiRuntimeConfigRepo configRepo, DeltafiRuntimeConfigurationValidator configurationValidator) {
        this.configRepo = configRepo;
        this.configurationValidator = configurationValidator;
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

    DeltafiRuntimeConfiguration getConfig() {
        return config;
    }

    void setConfig(DeltafiRuntimeConfiguration config) {
        this.config = config;
    }

    public String exportConfigAsYaml() {
        return yaml.dumpAsMap(config);
    }

    public String replaceConfig(String configInput) {
        DeltafiRuntimeConfiguration incoming = yaml.load(configInput);
        ensureNamesSet(incoming);
        saveConfig(incoming);
        return exportConfigAsYaml();
    }

    public String mergeConfig(String configInput) {
        DeltafiRuntimeConfiguration incoming = yaml.load(configInput);
        ensureNamesSet(incoming);
        DeltafiRuntimeConfiguration existing = getCurrentConfig();

        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getTransformActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getLoadActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getEnrichActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getFormatActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getValidateActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getEgressActions);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getIngressFlows);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getEgressFlows);
        mergeMap(incoming, existing, DeltafiRuntimeConfiguration::getDeleteActions);

        saveConfig(existing);
        return exportConfigAsYaml();
    }

    public Optional<IngressFlowConfiguration> getIngressFlow(String flow) {
        return Optional.ofNullable(getConfigFromMap(DeltafiRuntimeConfiguration::getIngressFlows, flow));
    }

    public Collection<EgressFlowConfiguration> getEgressFlows() {
        return config.getEgressFlows().values();
    }

    @SuppressWarnings("unused")
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
            saveConfig(current);
        }
        return removing;
    }

    private int removeByTypeAndName(ConfigType configType, String configName) {
        DeltafiRuntimeConfiguration current = getCurrentConfig();
        Map<String, ? extends DeltaFiConfiguration> toClear = current.getMapByType(configType);
        DeltaFiConfiguration removed = toClear.remove(configName);
        if (Objects.nonNull(removed)) {
            saveConfig(current);
            return 1;
        }
        return 0;
    }

    private int removeAll() {
        DeltafiRuntimeConfiguration current = getCurrentConfig();

        int removed = 0;
        for (ConfigType configType : ConfigType.values()) {
            Map<String, ? extends DeltaFiConfiguration> toClear = current.getMapByType(configType);
            removed += toClear.size();
            toClear.clear();
        }

        saveConfig(current);
        return removed;
    }

    private DeltafiRuntimeConfiguration getCurrentConfig() {
        return configRepo.findById(DeltafiRuntimeConfiguration.ID_CONSTANT).orElseThrow(() -> new IllegalStateException("Could not find the deltafi configuration"));
    }

    public <C extends DeltaFiConfiguration> C getConfigFromMap(Function<DeltafiRuntimeConfiguration, Map<String, C>> mapFunction, String name) {
        C actionConfig = mapFunction.apply(config).get(name);

        if (isNull(actionConfig)) {
            refreshConfig();
            actionConfig = mapFunction.apply(config).get(name);
        }

        return actionConfig;
    }

    private <C extends DeltaFiConfiguration> void mergeMap(DeltafiRuntimeConfiguration incoming, DeltafiRuntimeConfiguration existing, Function<DeltafiRuntimeConfiguration, Map<String, C>> mapFunction) {
        Map<String, C> incomingMap = mapFunction.apply(incoming);
        Map<String, C> existingMap = mapFunction.apply(existing);

        for (Map.Entry<String, C> entry : incomingMap.entrySet()) {
            existingMap.put(entry.getKey(), entry.getValue());
        }
    }

    private void ensureNamesSet(DeltafiRuntimeConfiguration incomingConfig) {
        incomingConfig.actionMaps().map(Map::entrySet).flatMap(Set::stream).forEach(this::ensureConfigNameIsSet);
        incomingConfig.deltafiMaps().map(Map::entrySet).flatMap(Set::stream).forEach(this::ensureConfigNameIsSet);
    }

    private void ensureConfigNameIsSet(Map.Entry<String, ? extends DeltaFiConfiguration> entry) {
        ensureConfigNameIsSet(entry.getKey(), entry.getValue());
    }

    private void ensureConfigNameIsSet(String key, DeltaFiConfiguration configItem) {
        if (isNull(configItem.getName())) {
            configItem.setName(key);
        }
    }

    private void saveConfig(DeltafiRuntimeConfiguration updated) {
        List<String> errors = configurationValidator.validate(updated);
        if (!errors.isEmpty()) {
            throw new DeltafiConfigurationException(String.join("; ", errors));
        }

        config = configRepo.save(updated);
    }

}
