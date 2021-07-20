package org.deltafi.dgs.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.dgs.api.types.ConfigType;
import org.deltafi.dgs.configuration.DeltaFiConfiguration;
import org.deltafi.dgs.configuration.DomainEndpointConfiguration;
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
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.k8s.GatewayConfigService;
import org.deltafi.dgs.repo.DeltaFiConfigRepo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeltaFiConfigService {

    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final DeltaFiConfigRepo deltaFiConfigRepo;
    private final ObjectProvider<GatewayConfigService> gatewayConfigService;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public DeltaFiConfigService(DeltaFiConfigRepo deltaFiConfigRepo, @Lazy ObjectProvider<GatewayConfigService> gatewayConfigService) {
        this.deltaFiConfigRepo = deltaFiConfigRepo;
        this.gatewayConfigService = gatewayConfigService;
    }

    @Cacheable("ingressFlow")
    public Optional<IngressFlowConfiguration> getIngressFlow(String flow) {
        return Optional.ofNullable(deltaFiConfigRepo.findIngressFlowConfig(flow));
    }

    @Cacheable("egressFlows")
    public Collection<EgressFlowConfiguration> getEgressFlows() {
        return deltaFiConfigRepo.findAllEgressFlows();
    }

    @Cacheable("optionalEgressFlow")
    public Optional<EgressFlowConfiguration> getEgressFlow(String flow) {
        return Optional.ofNullable(deltaFiConfigRepo.findEgressFlowConfig(flow));
    }

    @Cacheable("egressFlow")
    public EgressFlowConfiguration getEgressFlowForAction(String egressAction) {
        return deltaFiConfigRepo.findEgressFlowForAction(egressAction);
    }

    @Cacheable("loadAction")
    public LoadActionConfiguration getLoadAction(String loadAction) {
        return deltaFiConfigRepo.findLoadAction(loadAction);
    }

    @Cacheable("enrichAction")
    public EnrichActionConfiguration getEnrichAction(String enrichAction) {
        return deltaFiConfigRepo.findEnrichAction(enrichAction);
    }

    @Cacheable("formatAction")
    public FormatActionConfiguration getFormatAction(String formatAction) {
        return deltaFiConfigRepo.findFormatAction(formatAction);
    }

    @Cacheable("domainEndpoints")
    public List<DomainEndpointConfiguration> getDomainEndpoints() {
        return deltaFiConfigRepo.findAllDomainEndpoints();
    }

    public List<String> getEnrichActions() {
        return getActionNamesByType(ConfigType.ENRICH_ACTION);
    }

    public List<String> getFormatActions() {
        return getActionNamesByType(ConfigType.FORMAT_ACTION);
    }

    @Cacheable("loadGroups")
    public List<String> getLoadGroupActions(String loadGroupName) {
        LoadActionGroupConfiguration loadActionGroupConfiguration = deltaFiConfigRepo.findLoadActionGroup(loadGroupName);
        if (Objects.nonNull(loadActionGroupConfiguration)) {
            return loadActionGroupConfiguration.getLoadActions();
        }

        return Collections.emptyList();
    }

    @Cacheable("config")
    public List<DeltaFiConfiguration> getConfigs(ConfigQueryInput actionQueryInput) {
        if (Objects.nonNull(actionQueryInput)) {
            ConfigType configType = mapper.convertValue(actionQueryInput.getConfigType(), ConfigType.class);
            if (Objects.nonNull(actionQueryInput.getName())) {
                Optional<DeltaFiConfiguration> actionConfiguration = deltaFiConfigRepo.findByNameAndConfigType(actionQueryInput.getName(), configType);
                return actionConfiguration.map(Collections::singletonList).orElse(Collections.emptyList());
            } else {
                return deltaFiConfigRepo.findAllByConfigType(configType);
            }
        }
        return deltaFiConfigRepo.findAll();
    }

    @Cacheable("actionNames")
    public List<String> getActionNamesByType(ConfigType configType) {
        return deltaFiConfigRepo.findAllByConfigType(configType).stream().map(DeltaFiConfiguration::getName).collect(Collectors.toList());
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    public IngressFlowConfiguration saveIngressFlow(IngressFlowConfigurationInput ingressFlowConfigurationInput) {
        return saveConfig(ingressFlowConfigurationInput, IngressFlowConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    public EgressFlowConfiguration saveEgressFlow(EgressFlowConfigurationInput egressFlowConfigurationInput) {
        EgressFlowConfiguration egressFlowConfiguration = mapper.convertValue(egressFlowConfigurationInput, EgressFlowConfiguration.class);
        egressFlowConfiguration.setEgressAction(EgressConfiguration.egressActionName(egressFlowConfiguration.getName()));
        return deltaFiConfigRepo.upsertConfiguration(egressFlowConfiguration, EgressFlowConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    public TransformActionConfiguration saveTransformAction(TransformActionConfigurationInput transformActionConfigurationInput) {
        return saveConfig(transformActionConfigurationInput, TransformActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    public LoadActionConfiguration saveLoadAction(LoadActionConfigurationInput loadActionConfigurationInput) {
        LoadActionConfiguration fromInput = mapper.convertValue(loadActionConfigurationInput, LoadActionConfiguration.class);
        fromInput.setRequiresMetadata(KeyValueConverter.convertKeyValueInputs(loadActionConfigurationInput.getRequiresMetadataKeyValues()));
        return deltaFiConfigRepo.upsertConfiguration(fromInput, LoadActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    public EnrichActionConfiguration saveEnrichAction(EnrichActionConfigurationInput enrichActionConfigurationInput) {
        return saveConfig(enrichActionConfigurationInput, EnrichActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    public FormatActionConfiguration saveFormatAction(FormatActionConfigurationInput formatActionConfigurationInput) {
        return saveConfig(formatActionConfigurationInput, FormatActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    public ValidateActionConfiguration saveValidateAction(ValidateActionConfigurationInput validateActionConfigurationInput) {
        return saveConfig(validateActionConfigurationInput, ValidateActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    public EgressActionConfiguration saveEgressAction(EgressActionConfigurationInput egressActionConfigurationInput) {
        return saveConfig(egressActionConfigurationInput, EgressActionConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    public DomainEndpointConfiguration saveDomainEndpoint(DomainEndpointConfigurationInput domainEndpointConfigurationInput) {
        DomainEndpointConfiguration domainEndpointConfiguration = saveConfig(domainEndpointConfigurationInput, DomainEndpointConfiguration.class);
        reloadApollo();
        return domainEndpointConfiguration;
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    public LoadActionGroupConfiguration saveLoadActionGroup(LoadActionGroupConfigurationInput loadActionGroup) {
        return saveConfig(loadActionGroup, LoadActionGroupConfiguration.class);
    }

    public long removeConfigs(ConfigQueryInput configQueryInput) {
        long removed = doRemoveConfigs(configQueryInput);

        if (removed > 0 && shouldRefreshApollo(configQueryInput)) {
            reloadApollo();
        }

        return removed;
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    public long doRemoveConfigs(ConfigQueryInput configQuery) {
        if (Objects.nonNull(configQuery)) {
            ConfigType configType = mapper.convertValue(configQuery.getConfigType(), ConfigType.class);
            if (Objects.nonNull(configQuery.getName())) {
                return deltaFiConfigRepo.deleteByNameAndConfigType(configQuery.getName(), configType);
            } else {
                return deltaFiConfigRepo.deleteAllByConfigType(configType);
            }
        }
        return deltaFiConfigRepo.deleteAllWithCount();
    }

    private void reloadApollo() {
        gatewayConfigService.ifAvailable(GatewayConfigService::refreshApolloConfig);
    }

    private <C extends DeltaFiConfiguration> C saveConfig(Object input, Class<C> clazz) {
        C fromInput = mapper.convertValue(input, clazz);
        return deltaFiConfigRepo.upsertConfiguration(fromInput, clazz);
    }

    private boolean shouldRefreshApollo(ConfigQueryInput input) {
        return Objects.isNull(input) || org.deltafi.dgs.generated.types.ConfigType.DOMAIN_ENDPOINT.equals(input.getConfigType());
    }
}
