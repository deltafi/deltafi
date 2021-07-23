package org.deltafi.dgs.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.dgs.configuration.*;
import org.deltafi.dgs.repo.ActionConfigRepo;
import org.deltafi.dgs.repo.DeltaFiConfigRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Load DeltaFiConfigurations based on the application properties
 * at startup when this is enabled.
 */
@Service
public class ConfigLoaderService {

    @Value("${deltafi.configSource.path:classpath:deltafi-config.json}")
    private Resource resource;

    private static final ObjectMapper mapper = new ObjectMapper();

    private final DeltaFiProperties deltaFiProperties;
    private final ActionConfigRepo actionConfigRepo;
    private final DeltaFiConfigRepo deltaFiConfigRepo;

    private Consumer<ActionConfiguration> persistActionConfigMethod;
    private Consumer<DeltaFiConfiguration> persistDeltafiConfigMethod;

    public ConfigLoaderService(DeltaFiProperties deltaFiProperties, DeltaFiConfigRepo deltaFiConfigRepo, ActionConfigRepo actionConfigRepo) {
        this.deltaFiProperties = deltaFiProperties;
        this.deltaFiConfigRepo = deltaFiConfigRepo;
        this.actionConfigRepo = actionConfigRepo;
    }

    @PostConstruct
    public void initConfig() throws IOException {
        if (resource.exists() && resource.isReadable()) {
            DeltafiRuntimeConfiguration config = mapper.readValue(resource.getInputStream(), DeltafiRuntimeConfiguration.class);
            loadActionConfigs(config);
            loadDeltafiConfigs(config);
        }
    }

    @CacheEvict(allEntries = true, cacheNames = { "loadAction", "enrichAction", "formatAction", "actionConfig", "actionConfigs", "actionNames" })
    public void loadActionConfigs(DeltafiRuntimeConfiguration config) {
        switch (deltaFiProperties.getConfigSource().getActions()) {
            case EXTERNAL:
                return;
            case OVERWRITE_FROM_PROPERTY:
                persistActionConfigMethod = this::upsertConfig;
                break;
            case DEFAULT_FROM_PROPERTY:
                persistActionConfigMethod = this::saveIfNew;
                break;
            case RELOAD_FROM_PROPERTY:
                actionConfigRepo.deleteAll();
                persistActionConfigMethod = this::save;
                break;
        }
        doLoadActionConfigs(config);
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "egressFlowNames", "domainEndpoints", "loadGroups", "config" })
    public void loadDeltafiConfigs(DeltafiRuntimeConfiguration configs) {
        switch (deltaFiProperties.getConfigSource().getFlows()) {
            case EXTERNAL:
                return;
            case OVERWRITE_FROM_PROPERTY:
                persistDeltafiConfigMethod = this::upsertConfig;
                break;
            case DEFAULT_FROM_PROPERTY:
                persistDeltafiConfigMethod = this::saveIfNew;
                break;
            case RELOAD_FROM_PROPERTY:
                deltaFiConfigRepo.deleteAll();
                persistDeltafiConfigMethod = this::save;
                break;
        }
        doLoadDeltafiConfigs(configs);
    }

    public void doLoadActionConfigs(DeltafiRuntimeConfiguration config) {
        config.getTransformActions().forEach(this::persistActionConfig);
        config.getLoadActions().forEach(this::persistActionConfig);
        config.getEnrichActions().forEach(this::persistActionConfig);
        config.getFormatActions().forEach(this::persistActionConfig);
        config.getValidateActions().forEach(this::persistActionConfig);
        config.getEgressActions().forEach(this::persistActionConfig);
        config.getTransformActions().forEach(this::persistActionConfig);
    }

    public void doLoadDeltafiConfigs(DeltafiRuntimeConfiguration config) {
        config.getIngressFlows().forEach(this::persistDeltafiConfig);
        config.getEgressFlows().forEach(this::saveEgressFlowConfiguration);
        config.getLoadGroups().forEach(this::persistDeltafiConfig);
    }

    private void saveEgressFlowConfiguration(EgressFlowConfiguration egressFlowConfiguration) {
        if (Objects.isNull(egressFlowConfiguration.getEgressAction()) || egressFlowConfiguration.getEgressAction().isBlank()) {
            egressFlowConfiguration.setEgressAction(EgressConfiguration.egressActionName(egressFlowConfiguration.getName()));
        }
        persistDeltafiConfig(egressFlowConfiguration);
    }

    private void persistDeltafiConfig(DeltaFiConfiguration deltafiConfig) {
        deltafiConfig.setModified(OffsetDateTime.now());
        persistDeltafiConfigMethod.accept(deltafiConfig);
    }

    public void persistActionConfig(ActionConfiguration configuration) {
        configuration.setModified(OffsetDateTime.now());
        persistActionConfigMethod.accept(configuration);
    }

    public void upsertConfig(DeltaFiConfiguration configuration) {
        deltaFiConfigRepo.upsertConfiguration(configuration, DeltaFiConfiguration.class);
    }

    public void saveIfNew(DeltaFiConfiguration configuration) {
        if (!deltaFiConfigRepo.exists(configuration)) {
            deltaFiConfigRepo.save(configuration);
        }
    }

    public void save(DeltaFiConfiguration configuration) {
        deltaFiConfigRepo.save(configuration);
    }

    public void upsertConfig(ActionConfiguration configuration) {
        actionConfigRepo.upsertConfiguration(configuration, ActionConfiguration.class);
    }

    public void saveIfNew(ActionConfiguration configuration) {
        if (!actionConfigRepo.exists(configuration)) {
            actionConfigRepo.save(configuration);
        }
    }

    public void save(ActionConfiguration configuration) {
        actionConfigRepo.save(configuration);
    }


}
