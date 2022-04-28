package org.deltafi.core.domain.plugin;

import org.assertj.core.api.Assertions;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.ActionDescriptor;
import org.deltafi.core.domain.generated.types.Result;
import org.deltafi.core.domain.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PluginRegistryServiceTest {

    private static final PluginCoordinates PLUGIN_COORDINATES_1 = new PluginCoordinates("org.mock", "plugin-1", "1.0.0");
    private static final PluginCoordinates PLUGIN_COORDINATES_2 = new PluginCoordinates("org.mock", "plugin-2", "1.0.0");

    @Mock
    ActionSchemaService actionSchemaService;

    @Mock
    PluginRepository pluginRepository;

    @Mock
    PluginVariableService pluginVariableService;

    @Mock
    IngressFlowPlanService ingressFlowPlanService;

    @Mock
    IngressFlowService ingressFlowService;

    @Mock
    EgressFlowPlanService egressFlowPlanService;

    @Mock
    EgressFlowService egressFlowService;

    @Mock
    PluginValidator pluginValidator;

    @Mock
    RedisService redisService;

    @InjectMocks
    PluginRegistryService pluginRegistryService;

    @Test
    public void addsPluginWithDependencies() {
        Mockito.when(pluginValidator.validate(Mockito.any())).thenReturn(List.of());

        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        Result result = pluginRegistryService.addPlugin(plugin);

        assertTrue(result.getSuccess());
        ArgumentCaptor<Plugin> pluginArgumentCaptor = ArgumentCaptor.forClass(Plugin.class);
        Mockito.verify(pluginRepository).save(pluginArgumentCaptor.capture());
        assertEquals(plugin, pluginArgumentCaptor.getValue());
    }

    @Test
    public void addPluginMissingDependenciesReturnsErrors() {
        Mockito.when(pluginValidator.validate(Mockito.any())).thenReturn(List.of("error1", "error2"));

        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        Result result = pluginRegistryService.addPlugin(plugin);

        assertFalse(result.getSuccess());
        assertEquals(2, result.getErrors().size());
        assertEquals("error1", result.getErrors().get(0));
        assertEquals("error2", result.getErrors().get(1));
        Mockito.verify(pluginRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    public void uninstallNotFound() {
        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.empty());
        Result result = pluginRegistryService.uninstallPlugin(true, PLUGIN_COORDINATES_1);
        assertFalse(result.getSuccess());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().contains("Plugin not found"));

        Mockito.verifyNoInteractions(ingressFlowService);
        Mockito.verifyNoInteractions(egressFlowService);
        Mockito.verify(pluginRepository, Mockito.never()).deleteById(Mockito.any());
    }

    @Test
    public void uninstallFlowRunning() {
        Plugin plugin1 = makePlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));
        Mockito.when(ingressFlowService.findRunningFromPlugin(PLUGIN_COORDINATES_1)).thenReturn(List.of("mockIngress"));

        Result result = pluginRegistryService.uninstallPlugin(true, PLUGIN_COORDINATES_1);
        assertFalse(result.getSuccess());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().contains("The plugin has created the following ingress flows which are still running: mockIngress"));
    }

    @Test
    public void uninstallIsADependency() {
        Plugin plugin1 = makePlugin();
        Plugin plugin2 = makeDependencyPlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));
        Mockito.when(pluginRepository.findPluginsWithDependency(PLUGIN_COORDINATES_1)).thenReturn(List.of(plugin1, plugin2));

        Result result = pluginRegistryService.uninstallPlugin(true, PLUGIN_COORDINATES_1);
        assertFalse(result.getSuccess());

        Assertions.assertThat(result.getErrors()).hasSize(1).contains("The following plugins depend on this plugin: org.mock:plugin-1:1.0.0, org.mock:plugin-2:1.0.0");
    }

    @Test
    public void uninstallRunningAndADependency() {
        Plugin plugin1 = makePlugin();
        Plugin plugin2 = makeDependencyPlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));
        Mockito.when(pluginRepository.findPluginsWithDependency(PLUGIN_COORDINATES_1)).thenReturn(List.of(plugin1, plugin2));
        Mockito.when(ingressFlowService.findRunningFromPlugin(PLUGIN_COORDINATES_1)).thenReturn(List.of("mockIngress"));
        Mockito.when(egressFlowService.findRunningFromPlugin(PLUGIN_COORDINATES_1)).thenReturn(List.of("mockEgress"));

        Result result = pluginRegistryService.uninstallPlugin(true, PLUGIN_COORDINATES_1);
        assertFalse(result.getSuccess());

        Assertions.assertThat(result.getErrors()).hasSize(3)
                .contains("The plugin has created the following ingress flows which are still running: mockIngress")
                .contains("The plugin has created the following egress flows which are still running: mockEgress")
                .contains("The following plugins depend on this plugin: org.mock:plugin-1:1.0.0, org.mock:plugin-2:1.0.0");
    }

    @Test
    public void uninstallDryRun() {
        Plugin plugin1 = makePlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));

        Result result = pluginRegistryService.uninstallPlugin(true, PLUGIN_COORDINATES_1);
        assertTrue(result.getSuccess());

        // none of the removal steps should run for a dry-run
        Mockito.verify(pluginRepository, Mockito.never()).deleteById(Mockito.any());
        Mockito.verify(actionSchemaService, Mockito.never()).removeAllInList(Mockito.any());
        Mockito.verify(ingressFlowPlanService, Mockito.never()).rebuildFlowsForPlugin(Mockito.any());
        Mockito.verify(egressFlowPlanService, Mockito.never()).rebuildFlowsForPlugin(Mockito.any());
        Mockito.verify(pluginVariableService, Mockito.never()).removeVariables(Mockito.any());
    }

    @Test
    public void uninstallSuccess() {
        Plugin plugin1 = makePlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));

        Result result = pluginRegistryService.uninstallPlugin(false, PLUGIN_COORDINATES_1);
        assertTrue(result.getSuccess());


        Mockito.verify(pluginRepository).deleteById(PLUGIN_COORDINATES_1);
        Mockito.verify(ingressFlowPlanService).removeFlowsAndPlansBySourcePlugin(PLUGIN_COORDINATES_1);
        Mockito.verify(egressFlowPlanService).removeFlowsAndPlansBySourcePlugin(PLUGIN_COORDINATES_1);
        Mockito.verify(pluginVariableService).removeVariables(PLUGIN_COORDINATES_1);

        ArgumentCaptor<List<String>> actionCapture = ArgumentCaptor.forClass(List.class);
        Mockito.verify(actionSchemaService).removeAllInList(actionCapture.capture());


        List<String> actionNames = actionCapture.getValue();
        Assertions.assertThat(actionNames).hasSize(2).contains("action-1").contains("action-2");

        ArgumentCaptor<List<String>> redisCapture = ArgumentCaptor.forClass(List.class);
        Mockito.verify(redisService).dropQueues(redisCapture.capture());
        List<String> queueNames = redisCapture.getValue();
        Assertions.assertThat(queueNames).hasSize(2).contains("action-1").contains("action-2");
    }

    private Plugin makeDependencyPlugin() {
        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(PLUGIN_COORDINATES_2);
        plugin.setActions(List.of(
                ActionDescriptor.newBuilder().name("action-x").build(),
                ActionDescriptor.newBuilder().name("action-y").build()));
        plugin.setDependencies(List.of(PLUGIN_COORDINATES_1));
        return plugin;
    }

    private Plugin makePlugin() {
        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(PLUGIN_COORDINATES_1);
        plugin.setActions(List.of(
                ActionDescriptor.newBuilder().name("action-1").build(),
                ActionDescriptor.newBuilder().name("action-2").build()));
        return plugin;
    }
}
