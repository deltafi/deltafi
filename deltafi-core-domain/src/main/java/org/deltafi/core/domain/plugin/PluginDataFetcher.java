package org.deltafi.core.domain.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.PluginInput;
import org.deltafi.core.domain.generated.types.Result;

import java.util.Collection;

@DgsComponent
@RequiredArgsConstructor
public class PluginDataFetcher {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final PluginRegistryService pluginRegistryService;

    @DgsQuery
    public Collection<Plugin> plugins() {
        return pluginRegistryService.getPluginsWithVariables();
    }

    @DgsMutation
    public Result registerPlugin(PluginInput pluginInput) {
        return pluginRegistryService.addPlugin(OBJECT_MAPPER.convertValue(pluginInput, Plugin.class));
    }

    @DgsMutation
    public Result uninstallPlugin(boolean dryRun, PluginCoordinates pluginCoordinatesInput) {
        return pluginRegistryService.uninstallPlugin(dryRun, pluginCoordinatesInput);
    }
}
