package org.deltafi.core.domain.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration;
import com.netflix.graphql.dgs.autoconfig.DgsExtendedScalarsAutoConfiguration;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.common.resource.Resource;
import org.deltafi.core.domain.generated.client.PluginsGraphQLQuery;
import org.deltafi.core.domain.generated.client.PluginsProjectionRoot;
import org.deltafi.core.domain.generated.client.RegisterPluginGraphQLQuery;
import org.deltafi.core.domain.generated.types.PackagemediaType;
import org.deltafi.core.domain.generated.types.PackageType;
import org.deltafi.core.domain.generated.types.PluginInput;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = { DgsAutoConfiguration.class, DgsExtendedScalarsAutoConfiguration.class, PluginDataFetcher.class })
public class PluginDataFetcherTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final PluginsProjectionRoot PLUGINS_PROJECTION_ROOT = new PluginsProjectionRoot()
            .pluginCoordinates()
            .groupId()
            .artifactId()
            .version().parent()
            .description()
            .packages()
            .packageCoordinates()
            .groupId()
            .artifactId()
            .version()
            .type().parent().parent()
            .mediaType().parent()
            .location().parent()
            .dependencies()
            .groupId()
            .artifactId()
            .version().parent()
            .propertySets()
            .id()
            .displayName()
            .description()
            .properties()
            .key()
            .description()
            .defaultValue()
            .refreshable()
            .editable()
            .hidden()
            .value().parent().parent()
            .actions()
            .name()
            .consumes()
            .produces()
            .requiresDomains().parent()
            .domains();

    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @MockBean
    PluginRegistryService pluginRegistryService;

    @Test
    public void getsPlugins() throws IOException {
        Plugin plugin1 = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), Plugin.class);
        Plugin plugin2 = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), Plugin.class);
        Mockito.when(pluginRegistryService.getPlugins()).thenReturn(List.of(plugin1, plugin2));

        PluginsGraphQLQuery pluginsGraphQLQuery = PluginsGraphQLQuery.newRequest().build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(pluginsGraphQLQuery, PLUGINS_PROJECTION_ROOT);

        List<org.deltafi.core.domain.generated.types.Plugin> plugins =
                dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
                "data.plugins[*]", new TypeRef<>() {});

        assertEquals(2, plugins.size());

        validatePlugin1(plugins.get(0));
    }

    @Test
    public void registersPlugin() throws IOException {
        PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginInput.class);
        RegisterPluginGraphQLQuery registerPluginGraphQLQuery = RegisterPluginGraphQLQuery.newRequest().pluginInput(pluginInput).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(registerPluginGraphQLQuery, PLUGINS_PROJECTION_ROOT);

        Plugin plugin = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
                "data." + registerPluginGraphQLQuery.getOperationName(), Plugin.class);

        validatePlugin1(plugin);

        ArgumentCaptor<Plugin> pluginArgumentCaptor = ArgumentCaptor.forClass(Plugin.class);
        Mockito.verify(pluginRegistryService).addPlugin(pluginArgumentCaptor.capture());
        validatePlugin1(pluginArgumentCaptor.getValue());
    }

    private void validatePlugin1(org.deltafi.core.domain.generated.types.Plugin plugin1) {
        assertEquals("org.deltafi", plugin1.getPluginCoordinates().getGroupId());
        assertEquals("plugin-1", plugin1.getPluginCoordinates().getArtifactId());
        assertEquals("1.0.0", plugin1.getPluginCoordinates().getVersion());

        assertEquals("This is a test plugin", plugin1.getDescription());

        assertEquals(3, plugin1.getPackages().size());
        assertEquals("org.deltafi", plugin1.getPackages().get(0).getPackageCoordinates().getGroupId());
        assertEquals("package-1", plugin1.getPackages().get(0).getPackageCoordinates().getArtifactId());
        assertEquals("1.0.0", plugin1.getPackages().get(0).getPackageCoordinates().getVersion());
        assertEquals(PackageType.helm, plugin1.getPackages().get(0).getPackageCoordinates().getType());
        assertEquals(PackagemediaType.both, plugin1.getPackages().get(0).getMediaType());
        assertEquals("location of package-1", plugin1.getPackages().get(0).getLocation());
        assertEquals("package-2", plugin1.getPackages().get(1).getPackageCoordinates().getArtifactId());

        assertEquals(2, plugin1.getDependencies().size());
        assertEquals("org.deltafi", plugin1.getDependencies().get(0).getGroupId());
        assertEquals("dependency-1", plugin1.getDependencies().get(0).getArtifactId());
        assertEquals("1.0.0", plugin1.getDependencies().get(0).getVersion());
        assertEquals("dependency-2", plugin1.getDependencies().get(1).getArtifactId());

        assertEquals(2, plugin1.getPropertySets().size());
        assertEquals("propertySet1", plugin1.getPropertySets().get(0).getId());
        assertEquals("Property Set 1", plugin1.getPropertySets().get(0).getDisplayName());
        assertEquals("A description of property set 1", plugin1.getPropertySets().get(0).getDescription());
        assertEquals(2, plugin1.getPropertySets().get(0).getProperties().size());
        assertEquals("property1", plugin1.getPropertySets().get(0).getProperties().get(0).getKey());
        assertEquals("A description of property 1", plugin1.getPropertySets().get(0).getProperties().get(0).getDescription());
        assertEquals("property1Default", plugin1.getPropertySets().get(0).getProperties().get(0).getDefaultValue());
        assertTrue(plugin1.getPropertySets().get(0).getProperties().get(0).getRefreshable());
        assertTrue(plugin1.getPropertySets().get(0).getProperties().get(0).getEditable());
        assertFalse(plugin1.getPropertySets().get(0).getProperties().get(0).getHidden());
        assertEquals("property1Value", plugin1.getPropertySets().get(0).getProperties().get(0).getValue());
        assertEquals("property4Value", plugin1.getPropertySets().get(1).getProperties().get(1).getValue());
        assertEquals("propertySet2", plugin1.getPropertySets().get(1).getId());

        assertEquals(2, plugin1.getActions().size());
        assertEquals("org.deltafi.test.actions.TestAction1", plugin1.getActions().get(0).getName());
        assertEquals("org.deltafi.test.actions.TestAction2", plugin1.getActions().get(1).getName());
        assertEquals("dataFormat1", plugin1.getActions().get(1).getConsumes());
        assertEquals("dataFormat2", plugin1.getActions().get(1).getProduces());
        assertEquals(1, plugin1.getActions().get(1).getRequiresDomains().size());
        assertEquals("test", plugin1.getActions().get(1).getRequiresDomains().get(0));

        assertEquals(1, plugin1.getDomains().size());
        assertEquals("test", plugin1.getDomains().get(0));
    }
}
