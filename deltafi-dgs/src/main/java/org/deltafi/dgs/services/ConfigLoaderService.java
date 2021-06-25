package org.deltafi.dgs.services;

import org.deltafi.dgs.configuration.*;
import org.deltafi.dgs.repo.DeltaFiConfigRepo;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Load DeltaFiConfigurations based on the application properties
 * at startup when this is enabled.
 */
@Service
public class ConfigLoaderService {

    private final DeltaFiProperties deltaFiProperties;
    private final DeltaFiConfigRepo deltaFiConfigRepo;
    private Consumer<DeltaFiConfiguration> persistActionMethod;
    private Consumer<DeltaFiConfiguration> persistFlowMethod;

    public ConfigLoaderService(DeltaFiProperties deltaFiProperties, DeltaFiConfigRepo deltaFiConfigRepo) {
        this.deltaFiProperties = deltaFiProperties;
        this.deltaFiConfigRepo = deltaFiConfigRepo;
    }

    @PostConstruct
    public void loadProperties() {
        loadActions(deltaFiProperties.getConfigSource().getActions());
        loadFlows(deltaFiProperties.getConfigSource().getFlows());
    }

    public void loadActions(ConfigSource.Source actionConfigSource) {
        switch (actionConfigSource) {
            case EXTERNAL:
                return;
            case OVERWRITE_FROM_PROPERTY:
                persistActionMethod = this::upsertConfig;
                break;
            case DEFAULT_FROM_PROPERTY:
                persistActionMethod = this::saveIfNew;
                break;
            case RELOAD_FROM_PROPERTY:
                deltaFiConfigRepo.deleteActionConfigs();
                persistActionMethod = this::save;
                break;
        }
        loadActions();
    }

    public void loadFlows(ConfigSource.Source flowConfigSource) {
        switch (flowConfigSource) {
            case EXTERNAL:
                return;
            case OVERWRITE_FROM_PROPERTY:
                persistFlowMethod = this::upsertConfig;
                break;
            case DEFAULT_FROM_PROPERTY:
                persistFlowMethod = this::saveIfNew;
                break;
            case RELOAD_FROM_PROPERTY:
                deltaFiConfigRepo.deleteFlowConfigs();
                persistFlowMethod = this::save;
                break;
        }
        loadFlows();
    }

    public void loadActions() {
        deltaFiProperties.getTransformActions().forEach(this::setNameAndSaveConfig);
        deltaFiProperties.getLoadActions().forEach(this::setNameAndSaveConfig);
        deltaFiProperties.getEnrichActions().forEach(this::setNameAndSaveConfig);
        deltaFiProperties.getFormatActions().forEach(this::setNameAndSaveConfig);
        deltaFiProperties.getLoadGroups().forEach(this::saveLoadGroups);
        deltaFiProperties.getValidateActions().forEach(this::saveValidateConfig);
        deltaFiProperties.getEgress().getEgressFlows().keySet().forEach(this::saveEgressAction);
    }

    public void loadFlows() {
        deltaFiProperties.getIngress().getIngressFlows().forEach(this::saveIngressFlowConfiguration);
        deltaFiProperties.getEgress().getEgressFlows().forEach(this::saveEgressFlowConfiguration);
    }

    private void saveIngressFlowConfiguration(String name, IngressFlowConfiguration ingressFlowConfiguration) {
        ingressFlowConfiguration.setName(name);
        ingressFlowConfiguration.setModified(OffsetDateTime.now());
        persistFlowMethod.accept(ingressFlowConfiguration);
    }

    private void saveEgressFlowConfiguration(String name, EgressFlowConfiguration egressFlowConfiguration) {
        egressFlowConfiguration.setName(name);
        egressFlowConfiguration.setEgressAction(EgressConfiguration.egressActionName(name));
        egressFlowConfiguration.setModified(OffsetDateTime.now());
        persistFlowMethod.accept(egressFlowConfiguration);
    }

    public void saveValidateConfig(String name) {
        ValidateActionConfiguration actionConfiguration = new ValidateActionConfiguration();
        actionConfiguration.setName(name);
        persistActionConfig(actionConfiguration);
    }

    public void saveEgressAction(String name) {
        EgressActionConfiguration actionConfiguration = new EgressActionConfiguration();
        actionConfiguration.setName(EgressConfiguration.egressActionName(name));
        persistActionConfig(actionConfiguration);
    }

    public void saveLoadGroups(String name, List<String> actions) {
        LoadActionGroupConfiguration loadActionGroupConfiguration = new LoadActionGroupConfiguration();
        loadActionGroupConfiguration.setName(name);
        loadActionGroupConfiguration.setLoadActions(actions);
        persistActionConfig(loadActionGroupConfiguration);
    }

    public void setNameAndSaveConfig(String name, DeltaFiConfiguration config) {
        config.setName(name);
        persistActionConfig(config);
    }

    public void persistActionConfig(DeltaFiConfiguration configuration) {
        configuration.setModified(OffsetDateTime.now());
        persistActionMethod.accept(configuration);
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


}
