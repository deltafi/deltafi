package org.deltafi.dgs.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.dgs.api.types.ConfigType;
import org.deltafi.dgs.configuration.DeltaFiConfiguration;
import org.deltafi.dgs.configuration.DomainEndpointConfiguration;
import org.deltafi.dgs.configuration.EgressFlowConfiguration;
import org.deltafi.dgs.configuration.IngressFlowConfiguration;
import org.deltafi.dgs.configuration.LoadActionGroupConfiguration;
import org.deltafi.dgs.configuration.*;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.k8s.GatewayConfigService;
import org.deltafi.dgs.repo.DeltaFiConfigRepo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

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
    public EgressFlowConfiguration getEgressFlowByEgressActionName(String egressAction) {
        return deltaFiConfigRepo.findEgressFlowByEgressActionName(egressAction);
    }

    @Cacheable("egressFlowNames")
    public List<String> getEgressFlowsWithFormatAction(String formatAction) {
        return deltaFiConfigRepo.findEgressActionsWithFormatAction(formatAction);
    }

    @Cacheable("domainEndpoints")
    public List<DomainEndpointConfiguration> getDomainEndpoints() {
        return deltaFiConfigRepo.findAllDomainEndpoints();
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

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "egressFlowNames", "domainEndpoints", "loadGroups", "config" })
    public IngressFlowConfiguration saveIngressFlow(IngressFlowConfigurationInput ingressFlowConfigurationInput) {
        return saveDeltafiConfig(ingressFlowConfigurationInput, IngressFlowConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "egressFlowNames", "domainEndpoints", "loadGroups", "config" })
    public EgressFlowConfiguration saveEgressFlow(EgressFlowConfigurationInput egressFlowConfigurationInput) {
        EgressFlowConfiguration egressFlowConfiguration = mapper.convertValue(egressFlowConfigurationInput, EgressFlowConfiguration.class);
        egressFlowConfiguration.setEgressAction(EgressConfiguration.egressActionName(egressFlowConfiguration.getName()));
        return deltaFiConfigRepo.upsertConfiguration(egressFlowConfiguration, EgressFlowConfiguration.class);
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "egressFlowNames", "domainEndpoints", "loadGroups", "config" })
    public DomainEndpointConfiguration saveDomainEndpoint(DomainEndpointConfigurationInput domainEndpointConfigurationInput) {
        DomainEndpointConfiguration domainEndpointConfiguration = saveDeltafiConfig(domainEndpointConfigurationInput, DomainEndpointConfiguration.class);
        reloadApollo();
        return domainEndpointConfiguration;
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "egressFlowNames", "domainEndpoints", "loadGroups", "config" })
    public LoadActionGroupConfiguration saveLoadActionGroup(LoadActionGroupConfigurationInput loadActionGroup) {
        return saveDeltafiConfig(loadActionGroup, LoadActionGroupConfiguration.class);
    }

    public long removeDeltafiConfigs(ConfigQueryInput configQueryInput) {
        long removed = doRemoveConfigs(configQueryInput);

        if (removed > 0 && shouldRefreshApollo(configQueryInput)) {
            reloadApollo();
        }

        return removed;
    }

    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "egressFlowNames", "domainEndpoints", "loadGroups", "config" })
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

    private <C extends DeltaFiConfiguration> C saveDeltafiConfig(Object input, Class<C> clazz) {
        C fromInput = mapper.convertValue(input, clazz);
        return deltaFiConfigRepo.upsertConfiguration(fromInput, clazz);
    }

    private boolean shouldRefreshApollo(ConfigQueryInput input) {
        return Objects.isNull(input) || org.deltafi.dgs.generated.types.ConfigType.DOMAIN_ENDPOINT.equals(input.getConfigType());
    }
}
