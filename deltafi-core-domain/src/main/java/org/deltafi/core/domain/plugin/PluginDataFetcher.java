package org.deltafi.core.domain.plugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.domain.generated.types.PluginInput;

import java.util.Collection;

@DgsComponent
@RequiredArgsConstructor
public class PluginDataFetcher {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final PluginRegistryService pluginRegistryService;

    @DgsQuery
    public Collection<org.deltafi.core.domain.generated.types.Plugin> plugins() {
        return OBJECT_MAPPER.convertValue(pluginRegistryService.getPlugins(), new TypeReference<>() {});
    }

    @DgsMutation
    public Plugin registerPlugin(PluginInput pluginInput) {
        Plugin plugin = OBJECT_MAPPER.convertValue(pluginInput, Plugin.class);
        pluginRegistryService.addPlugin(plugin);
        return plugin;
    }
}
