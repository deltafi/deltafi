package org.deltafi.core.domain.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import org.deltafi.core.domain.configuration.EgressActionConfiguration;
import org.deltafi.core.domain.configuration.EnrichActionConfiguration;
import org.deltafi.core.domain.configuration.FormatActionConfiguration;
import org.deltafi.core.domain.configuration.LoadActionConfiguration;
import org.deltafi.core.domain.configuration.TransformActionConfiguration;
import org.deltafi.core.domain.configuration.ValidateActionConfiguration;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.generated.types.EgressFlowConfiguration;
import org.deltafi.core.domain.generated.types.IngressFlowConfiguration;
import org.deltafi.core.domain.generated.types.LoadActionGroupConfiguration;
import org.deltafi.core.domain.configuration.DeltaFiConfiguration;
import org.deltafi.core.domain.services.DeltaFiConfigService;

import java.util.List;

@DgsComponent
public class DeltafiConfigDatafetcher {

    private final DeltaFiConfigService deltaFiConfigService;

    public DeltafiConfigDatafetcher(DeltaFiConfigService deltaFiConfigService) {
        this.deltaFiConfigService = deltaFiConfigService;
    }

    @DgsMutation
    public TransformActionConfiguration registerTransformAction(TransformActionConfigurationInput transformActionConfiguration) {
        return deltaFiConfigService.saveTransformAction(transformActionConfiguration);
    }

    @DgsMutation
    public LoadActionConfiguration registerLoadAction(LoadActionConfigurationInput loadActionConfiguration) {
        return deltaFiConfigService.saveLoadAction(loadActionConfiguration);
    }

    @DgsMutation
    public EnrichActionConfiguration registerEnrichAction(EnrichActionConfigurationInput enrichActionConfiguration) {
        return deltaFiConfigService.saveEnrichAction(enrichActionConfiguration);
    }

    @DgsMutation
    public FormatActionConfiguration registerFormatAction(FormatActionConfigurationInput formatActionConfiguration) {
        return deltaFiConfigService.saveFormatAction(formatActionConfiguration);
    }

    @DgsMutation
    public ValidateActionConfiguration registerValidateAction(ValidateActionConfigurationInput validateActionConfiguration) {
        return deltaFiConfigService.saveValidateAction(validateActionConfiguration);
    }

    @DgsMutation
    public EgressActionConfiguration registerEgressAction(EgressActionConfigurationInput egressActionConfiguration) {
        return deltaFiConfigService.saveEgressAction(egressActionConfiguration);
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

    @DgsMutation
    public String replaceConfig(String configYaml) {
        return deltaFiConfigService.replaceConfig(configYaml);
    }

    @DgsMutation
    public String mergeConfig(String configYaml) {
        return deltaFiConfigService.mergeConfig(configYaml);
    }

    @DgsQuery
    public String exportConfigAsYaml() {
        return deltaFiConfigService.exportConfigAsYaml();
    }
}