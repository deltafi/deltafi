package org.deltafi.core.domain.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import org.deltafi.core.domain.configuration.DeltaFiConfiguration;
import org.deltafi.core.domain.generated.types.ConfigQueryInput;
import org.deltafi.core.domain.services.DeltaFiConfigService;

import java.util.List;

@DgsComponent
public class DeltafiConfigDatafetcher {

    private final DeltaFiConfigService deltaFiConfigService;

    public DeltafiConfigDatafetcher(DeltaFiConfigService deltaFiConfigService) {
        this.deltaFiConfigService = deltaFiConfigService;
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
