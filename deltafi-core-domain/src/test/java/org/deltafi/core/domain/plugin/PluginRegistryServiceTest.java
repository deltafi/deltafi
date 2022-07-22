/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.domain.plugin;

import org.assertj.core.api.Assertions;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.ActionDescriptor;
import org.deltafi.core.domain.generated.types.Result;
import org.deltafi.core.domain.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PluginRegistryServiceTest {

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
    EnrichFlowPlanService enrichFlowPlanService;

    @Mock
    EnrichFlowService enrichFlowService;

    @Mock
    EgressFlowPlanService egressFlowPlanService;

    @Mock
    EgressFlowService egressFlowService;

    @Mock
    PluginValidator pluginValidator;

    @Mock
    RedisService redisService;

    PluginRegistryService pluginRegistryService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        List<PluginCleaner> cleaners = List.of(ingressFlowPlanService, enrichFlowPlanService, egressFlowPlanService, pluginVariableService, actionSchemaService, redisService);
        List<PluginUninstallCheck> checkers = List.of(ingressFlowService, enrichFlowService, egressFlowService);
        pluginRegistryService = new PluginRegistryService(ingressFlowService, enrichFlowService, egressFlowService, pluginVariableService, pluginRepository, pluginValidator, actionSchemaService, checkers, cleaners);
    }

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
        Mockito.when(ingressFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following ingress flows which are still running: mockIngress");

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
        Mockito.when(ingressFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following ingress flows which are still running: mockIngress");
        Mockito.when(enrichFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following enrich flows which are still running: mockEnrich");
        Mockito.when(egressFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following egress flows which are still running: mockEgress");

        Result result = pluginRegistryService.uninstallPlugin(true, PLUGIN_COORDINATES_1);
        assertFalse(result.getSuccess());

        Assertions.assertThat(result.getErrors()).hasSize(4)
                .contains("The plugin has created the following ingress flows which are still running: mockIngress")
                .contains("The plugin has created the following enrich flows which are still running: mockEnrich")
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
        Mockito.verify(actionSchemaService, Mockito.never()).cleanupFor(Mockito.any());
        Mockito.verify(ingressFlowPlanService, Mockito.never()).cleanupFor(Mockito.any());
        Mockito.verify(enrichFlowPlanService, Mockito.never()).cleanupFor(Mockito.any());
        Mockito.verify(egressFlowPlanService, Mockito.never()).cleanupFor(Mockito.any());
        Mockito.verify(pluginVariableService, Mockito.never()).cleanupFor(Mockito.any());
    }

    @Test
    public void uninstallSuccess() {
        Plugin plugin1 = makePlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));

        Result result = pluginRegistryService.uninstallPlugin(false, PLUGIN_COORDINATES_1);
        assertTrue(result.getSuccess());

        Mockito.verify(pluginRepository).deleteById(PLUGIN_COORDINATES_1);
        Mockito.verify(ingressFlowPlanService).cleanupFor(plugin1);
        Mockito.verify(enrichFlowPlanService).cleanupFor(plugin1);
        Mockito.verify(egressFlowPlanService).cleanupFor(plugin1);
        Mockito.verify(pluginVariableService).cleanupFor(plugin1);
        Mockito.verify(actionSchemaService).cleanupFor(plugin1);
        Mockito.verify(redisService).cleanupFor(plugin1);
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
