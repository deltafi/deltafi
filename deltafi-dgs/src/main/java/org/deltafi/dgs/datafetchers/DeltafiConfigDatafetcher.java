package org.deltafi.dgs.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import org.deltafi.dgs.configuration.DeltaFiConfiguration;
import org.deltafi.dgs.configuration.DomainEndpointConfiguration;
import org.deltafi.dgs.configuration.EgressActionConfiguration;
import org.deltafi.dgs.configuration.EnrichActionConfiguration;
import org.deltafi.dgs.configuration.FormatActionConfiguration;
import org.deltafi.dgs.configuration.LoadActionConfiguration;
import org.deltafi.dgs.configuration.TransformActionConfiguration;
import org.deltafi.dgs.configuration.ValidateActionConfiguration;
import org.deltafi.dgs.generated.types.EgressFlowConfiguration;
import org.deltafi.dgs.generated.types.IngressFlowConfiguration;
import org.deltafi.dgs.generated.types.LoadActionGroupConfiguration;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.services.DeltaFiConfigService;

import java.util.List;

@DgsComponent
public class DeltafiConfigDatafetcher {

    private final DeltaFiConfigService deltaFiConfigService;

    public DeltafiConfigDatafetcher(DeltaFiConfigService deltaFiConfigService) {
        this.deltaFiConfigService = deltaFiConfigService;
    }

    @DgsMutation
    public DomainEndpointConfiguration registerDomainEndpoint(DomainEndpointConfigurationInput domainEndpointConfiguration) {
        return deltaFiConfigService.saveDomainEndpoint(domainEndpointConfiguration);
    }

    @DgsMutation
    public LoadActionGroupConfiguration addLoadActionGroup(LoadActionGroupConfigurationInput loadActionGroupConfiguration) {
        return deltaFiConfigService.saveLoadActionGroup(loadActionGroupConfiguration);
    }

    @DgsMutation
    public IngressFlowConfiguration addIngressFlow(IngressFlowConfigurationInput ingressFlowConfiguration) {
        return deltaFiConfigService.saveIngressFlow(ingressFlowConfiguration);
    }

    @DgsMutation
    public EgressFlowConfiguration addEgressFlow(EgressFlowConfigurationInput egressFlowConfiguration) {
        return deltaFiConfigService.saveEgressFlow(egressFlowConfiguration);
    }

    @DgsMutation
    public long removeDeltaFiConfigs(ConfigQueryInput configQuery) {
        return deltaFiConfigService.removeDeltafiConfigs(configQuery);
    }

    @DgsQuery
    public List<DeltaFiConfiguration> deltaFiConfigs(ConfigQueryInput configQuery) {
        return deltaFiConfigService.getConfigs(configQuery);
    }
}
