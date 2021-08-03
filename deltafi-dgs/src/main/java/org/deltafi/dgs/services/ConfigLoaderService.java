package org.deltafi.dgs.services;

import org.deltafi.dgs.configuration.*;
import org.deltafi.dgs.repo.DeltaFiRuntimeConfigRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Load DeltaFiConfigurations based on the application properties
 * at startup when this is enabled.
 */
@Service
public class ConfigLoaderService {

    @Value("${deltafi.configSource.path:classpath:deltafi-config.yaml}")
    private Resource resource;

    private final DeltaFiProperties deltaFiProperties;
    private final DeltaFiRuntimeConfigRepo runtimeConfigRepo;
    private final DeltaFiConfigService deltaFiConfigService;

    private DeltafiRuntimeConfiguration resourceConfig;
    private DeltafiRuntimeConfiguration existingConfig;

    private ConfigSource.Source actionSource;
    private ConfigSource.Source deltafiSource;

    public ConfigLoaderService(DeltaFiProperties deltaFiProperties, DeltaFiRuntimeConfigRepo runtimeConfigRepo, DeltaFiConfigService deltaFiConfigService) {
        this.deltaFiProperties = deltaFiProperties;
        this.runtimeConfigRepo = runtimeConfigRepo;
        this.deltaFiConfigService = deltaFiConfigService;
    }

    @PostConstruct
    public void initConfig() throws IOException {
        actionSource = deltaFiProperties.getConfigSource().getActions();
        deltafiSource = deltaFiProperties.getConfigSource().getFlows();

        if (hasWork() && resource.exists() && resource.isReadable()) {
            resourceConfig = loadResource();
            existingConfig = loadExisting();
            loadActionConfigs();
            loadDeltafiConfigs();
            runtimeConfigRepo.save(existingConfig);
        }

        deltaFiConfigService.refreshConfig();
    }

    public void loadActionConfigs() {
        if (ConfigSource.Source.RELOAD_FROM_PROPERTY.equals(actionSource)) {
            existingConfig.actionMaps().forEach(Map::clear);
        }
        mergeActionConfigs();
    }

    public void loadDeltafiConfigs() {
        if (ConfigSource.Source.RELOAD_FROM_PROPERTY.equals(deltafiSource)) {
            existingConfig.deltafiMaps().forEach(Map::clear);
        }
        mergeDeltaFiConfigs();
    }

    public void mergeActionConfigs() {
        mergeActionConfigs(DeltafiRuntimeConfiguration::getTransformActions);
        mergeActionConfigs(DeltafiRuntimeConfiguration::getLoadActions);
        mergeActionConfigs(DeltafiRuntimeConfiguration::getEnrichActions);
        mergeActionConfigs(DeltafiRuntimeConfiguration::getFormatActions);
        mergeActionConfigs(DeltafiRuntimeConfiguration::getValidateActions);
        mergeActionConfigs(DeltafiRuntimeConfiguration::getEgressActions);
    }


    public void mergeDeltaFiConfigs() {
        mergeDeltaFiConfigs(DeltafiRuntimeConfiguration::getIngressFlows);
        resourceConfig.getEgressFlows().values().forEach(this::setEgressActionName);
        mergeDeltaFiConfigs(DeltafiRuntimeConfiguration::getEgressFlows);
        mergeDeltaFiConfigs(DeltafiRuntimeConfiguration::getLoadGroups);
        mergeDeltaFiConfigs(DeltafiRuntimeConfiguration::getDomainEndpoints);
    }

    public void setEgressActionName(EgressFlowConfiguration egressFlowConfiguration) {
        if (Objects.isNull(egressFlowConfiguration.getEgressAction()) || egressFlowConfiguration.getEgressAction().isBlank()) {
            egressFlowConfiguration.setEgressAction(EgressConfiguration.egressActionName(egressFlowConfiguration.getName()));
        }
    }

    public <C extends DeltaFiConfiguration> void mergeActionConfigs(Function<DeltafiRuntimeConfiguration, Map<String, C>> configGetter) {
        mergeConfigs(configGetter, actionSource);
    }

    public <C extends DeltaFiConfiguration> void mergeDeltaFiConfigs(Function<DeltafiRuntimeConfiguration, Map<String, C>> configGetter) {
        mergeConfigs(configGetter, deltafiSource);
    }

    public <C extends DeltaFiConfiguration> void mergeConfigs(Function<DeltafiRuntimeConfiguration, Map<String, C>> configGetter, ConfigSource.Source mode) {
        mergeMap(mode, configGetter.apply(resourceConfig), configGetter.apply(existingConfig));
    }

    public <C extends DeltaFiConfiguration> void mergeMap(ConfigSource.Source mode, Map<String, C> fromFile, Map<String, C> existingConfig) {
        for (Map.Entry<String, C> entry : fromFile.entrySet()) {
            if (!existingConfig.containsKey(entry.getKey())) {
                setDefaultTimes(entry.getValue());
                existingConfig.put(entry.getKey(), entry.getValue());
            } else if(ConfigSource.Source.OVERWRITE_FROM_PROPERTY.equals(mode)) {
                updateTimes(entry.getValue(), existingConfig.get(entry.getKey()));
                existingConfig.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private void setDefaultTimes(DeltaFiConfiguration config) {
        OffsetDateTime now = OffsetDateTime.now();
        config.setCreated(now);
        config.setModified(now);
    }

    private void updateTimes(DeltaFiConfiguration toAdd, DeltaFiConfiguration existingConfig) {
        OffsetDateTime now = OffsetDateTime.now();
        toAdd.setModified(now);
        toAdd.setCreated(existingConfig.getCreated());
    }

    public DeltafiRuntimeConfiguration loadResource() throws IOException {
        Yaml yaml = new Yaml(new Constructor(DeltafiRuntimeConfiguration.class));
        return yaml.load(resource.getInputStream());
    }

    public DeltafiRuntimeConfiguration loadExisting() {
        return runtimeConfigRepo.findById(DeltafiRuntimeConfiguration.ID_CONSTANT).orElseGet(DeltafiRuntimeConfiguration::new);
    }

    public boolean hasWork() {
        return !ConfigSource.Source.EXTERNAL.equals(actionSource) && !ConfigSource.Source.EXTERNAL.equals(deltafiSource);
    }
}
