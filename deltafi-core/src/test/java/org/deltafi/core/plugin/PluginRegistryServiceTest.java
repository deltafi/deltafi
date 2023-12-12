/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.plugin;

import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.Plugin;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.PluginRegistration;
import org.deltafi.common.types.Variable;
import org.deltafi.core.security.DeltaFiUserDetailsService;
import org.deltafi.core.services.*;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.Result;
import org.deltafi.core.util.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PluginRegistryServiceTest {

    private static final PluginCoordinates PLUGIN_COORDINATES_1 = new PluginCoordinates("org.mock", "plugin-1", "1.0.0");
    private static final PluginCoordinates PLUGIN_COORDINATES_2 = new PluginCoordinates("org.mock", "plugin-2", "1.0.0");

    @Mock
    ActionDescriptorService actionDescriptorService;

    @Mock
    PluginRepository pluginRepository;

    @Mock
    PluginVariableService pluginVariableService;

    @Mock
    NormalizeFlowPlanService normalizeFlowPlanService;

    @Mock
    NormalizeFlowService normalizeFlowService;

    @Mock
    EnrichFlowPlanService enrichFlowPlanService;

    @Mock
    EnrichFlowService enrichFlowService;

    @Mock
    EgressFlowPlanService egressFlowPlanService;

    @Mock
    EgressFlowService egressFlowService;

    @Mock
    TransformFlowPlanService transformFlowPlanService;

    @Mock
    TransformFlowService transformFlowService;

    @Mock
    TimedIngressFlowPlanService timedIngressFlowPlanService;

    @Mock
    TimedIngressFlowService timedIngressFlowService;

    @Mock
    PluginValidator pluginValidator;

    @Mock
    ActionEventQueuePluginCleaner actionEventQueuePluginCleaner;

    @Mock
    SystemPluginService systemPluginService;

    @Mock
    FlowValidationService flowValidationService;

    PluginRegistryService pluginRegistryService;

    @BeforeEach
    public void setup() {
        List<PluginCleaner> cleaners = List.of(normalizeFlowPlanService, enrichFlowPlanService, egressFlowPlanService, transformFlowPlanService, timedIngressFlowPlanService, pluginVariableService, actionDescriptorService, actionEventQueuePluginCleaner);
        List<PluginUninstallCheck> checkers = List.of(normalizeFlowService, enrichFlowService, egressFlowService, transformFlowService, timedIngressFlowService);
        pluginRegistryService = new PluginRegistryService(normalizeFlowService, enrichFlowService, egressFlowService,
                transformFlowService, timedIngressFlowService, pluginRepository, pluginValidator, actionDescriptorService, pluginVariableService,
                normalizeFlowPlanService, enrichFlowPlanService, egressFlowPlanService, transformFlowPlanService, timedIngressFlowPlanService, systemPluginService, flowValidationService, checkers, cleaners);
    }

    @Test
    void addsPluginWithDependencies() {
        Mockito.when(pluginValidator.validate(Mockito.any())).thenReturn(List.of());

        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder().pluginCoordinates(plugin.getPluginCoordinates()).build();
        Result result = pluginRegistryService.register(pluginRegistration);

        assertTrue(result.isSuccess());
        ArgumentCaptor<Plugin> pluginArgumentCaptor = ArgumentCaptor.forClass(Plugin.class);
        Mockito.verify(pluginRepository).save(pluginArgumentCaptor.capture());
        assertEquals(plugin, pluginArgumentCaptor.getValue());
        Mockito.verify(flowValidationService).asyncRebuildInvalidFlows();
    }

    @Test
    void addPluginMissingDependenciesReturnsErrors() {
        Mockito.when(pluginValidator.validate(Mockito.any())).thenReturn(List.of("error1", "error2"));

        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder().pluginCoordinates(plugin.getPluginCoordinates()).build();
        Result result = pluginRegistryService.register(pluginRegistration);

        assertFalse(result.isSuccess());
        assertEquals(2, result.getErrors().size());
        assertEquals("error1", result.getErrors().get(0));
        assertEquals("error2", result.getErrors().get(1));
        Mockito.verify(pluginRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void uninstallNotFound() {
        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.empty());
        List<String> errors = pluginRegistryService.canBeUninstalled(PLUGIN_COORDINATES_1);

        assertThat(errors).hasSize(1).contains("Plugin not found");
    }

    @Test
    void uninstallFlowRunning() {
        Plugin plugin1 = makePlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));
        Mockito.when(normalizeFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following ingress flows which are still running: mockIngress");

        List<String> errors = pluginRegistryService.canBeUninstalled(PLUGIN_COORDINATES_1);

        assertThat(errors).hasSize(1).contains("The plugin has created the following ingress flows which are still running: mockIngress");
    }

    @Test
    void uninstallIsADependency() {
        Plugin plugin1 = makePlugin();
        Plugin plugin2 = makeDependencyPlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));
        Mockito.when(pluginRepository.findPluginsWithDependency(PLUGIN_COORDINATES_1)).thenReturn(List.of(plugin1, plugin2));

        List<String> errors = pluginRegistryService.canBeUninstalled(PLUGIN_COORDINATES_1);

        assertThat(errors).hasSize(1).contains("The following plugins depend on this plugin: org.mock:plugin-1:1.0.0, org.mock:plugin-2:1.0.0");
    }

    @Test
    void uninstallRunningAndADependency() {
        Plugin plugin1 = makePlugin();
        Plugin plugin2 = makeDependencyPlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));
        Mockito.when(pluginRepository.findPluginsWithDependency(PLUGIN_COORDINATES_1)).thenReturn(List.of(plugin1, plugin2));
        Mockito.when(normalizeFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following ingress flows which are still running: mockIngress");
        Mockito.when(enrichFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following enrich flows which are still running: mockEnrich");
        Mockito.when(egressFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following egress flows which are still running: mockEgress");

        List<String> errors = pluginRegistryService.canBeUninstalled(PLUGIN_COORDINATES_1);

        assertThat(errors).hasSize(4)
                .contains("The plugin has created the following ingress flows which are still running: mockIngress")
                .contains("The plugin has created the following enrich flows which are still running: mockEnrich")
                .contains("The plugin has created the following egress flows which are still running: mockEgress")
                .contains("The following plugins depend on this plugin: org.mock:plugin-1:1.0.0, org.mock:plugin-2:1.0.0");
    }

    @Test
    void uninstallSuccess() {
        Plugin plugin1 = makePlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));

        pluginRegistryService.uninstallPlugin(PLUGIN_COORDINATES_1);

        Mockito.verify(pluginRepository).deleteById(PLUGIN_COORDINATES_1);
        Mockito.verify(normalizeFlowPlanService).cleanupFor(plugin1);
        Mockito.verify(enrichFlowPlanService).cleanupFor(plugin1);
        Mockito.verify(egressFlowPlanService).cleanupFor(plugin1);
        Mockito.verify(pluginVariableService).cleanupFor(plugin1);
        Mockito.verify(actionDescriptorService).cleanupFor(plugin1);
        Mockito.verify(actionEventQueuePluginCleaner).cleanupFor(plugin1);
    }

    @Test
    void testUpdateSnapshot() {
        Plugin one = makePlugin();
        Plugin two = makePlugin();
        two.setPluginCoordinates(PLUGIN_COORDINATES_2);
        SystemSnapshot systemSnapshot = new SystemSnapshot();

        Mockito.when(pluginRepository.findAll()).thenReturn(List.of(one, two));
        pluginRegistryService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getInstalledPlugins()).hasSize(2).contains(PLUGIN_COORDINATES_1, PLUGIN_COORDINATES_2);
    }

    @Test
    void testResetFromSnapshot() {
        SystemSnapshot systemSnapshot = new SystemSnapshot();

        Plugin installedOnly = makePlugin();
        installedOnly.setPluginCoordinates(new PluginCoordinates("org.installed", "installed-plugin", "1.0.0"));
        Plugin newVersion = makePlugin();
        newVersion.setPluginCoordinates( new PluginCoordinates("org.mock", "plugin-2", "1.1.0"));


        Plugin inBoth = makePlugin();
        PluginCoordinates inSnapshotOnly = new PluginCoordinates("org.unique", "custom-plugin", "1.0.0");


        Mockito.when(pluginRepository.findAll()).thenReturn(List.of(installedOnly, newVersion, inBoth));

        systemSnapshot.setInstalledPlugins(Set.of(inBoth.getPluginCoordinates(), PLUGIN_COORDINATES_2, inSnapshotOnly));

        Result result = pluginRegistryService.resetFromSnapshot(systemSnapshot, true);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInfo()).hasSize(3)
                .contains("Installed plugin org.mock:plugin-2:1.1.0 was a different version at the time of the snapshot: org.mock:plugin-2:1.0.0")
                .contains("Plugin org.unique:custom-plugin:1.0.0 was installed at the time of the snapshot but is no longer installed")
                .contains("Installed plugin org.installed:installed-plugin:1.0.0 was not installed at the time of the snapshot");
    }

    @Test
    void testGetPluginsWithVariablesAsAdmin() {
        try (MockedStatic<DeltaFiUserDetailsService> userDetailsServiceMockedStatic = Mockito.mockStatic(DeltaFiUserDetailsService.class)) {
            testGetPluginsWithVariables(userDetailsServiceMockedStatic, true);
        }
    }

    @Test
    void testGetPluginsWithVariablesAsNonAdmin() {
        try (MockedStatic<DeltaFiUserDetailsService> userDetailsServiceMockedStatic = Mockito.mockStatic(DeltaFiUserDetailsService.class)) {
            testGetPluginsWithVariables(userDetailsServiceMockedStatic, false);
        }
    }

    private void testGetPluginsWithVariables(MockedStatic<DeltaFiUserDetailsService> userDetailsServiceMockedStatic, boolean isAdmin) {
        Plugin one = makeDependencyPlugin();
        Plugin two = makeDependencyPlugin();
        Variable variable = Util.buildNewVariable("setValue");
        one.setVariables(List.of(variable));
        Mockito.when(pluginRepository.findAll()).thenReturn(List.of(one, two));
        Mockito.when(pluginVariableService.getVariablesByPlugin(Mockito.any())).thenReturn(variableList());

        userDetailsServiceMockedStatic.when(DeltaFiUserDetailsService::currentUserCanViewMasked).thenReturn(isAdmin);
        List<Plugin> plugins = pluginRegistryService.getPluginsWithVariables();
        assertThat(plugins).hasSize(2);
        Consumer<Variable> checker = isAdmin ? this::verifyNoMaskedValues : this::verifyMaskedValues;
        plugins.stream().map(Plugin::getVariables).flatMap(Collection::stream).forEach(checker);
    }

    private void verifyNoMaskedValues(Variable variable) {
        assertThat(variable.getValue()).isNotEqualTo(Variable.MASK_STRING);
    }

    private void verifyMaskedValues(Variable variable) {
        if (variable.isMasked() && variable.getValue() != null) {
            assertThat(variable.getValue()).isEqualTo(Variable.MASK_STRING);
        } else {
            assertThat(variable.getValue()).isNotEqualTo(Variable.MASK_STRING);
        }
    }

    private Plugin makeDependencyPlugin() {
        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(PLUGIN_COORDINATES_2);
        plugin.setActions(List.of(
                ActionDescriptor.builder().name("action-x").build(),
                ActionDescriptor.builder().name("action-y").build()));
        plugin.setDependencies(List.of(PLUGIN_COORDINATES_1));
        return plugin;
    }

    private Plugin makePlugin() {
        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(PLUGIN_COORDINATES_1);
        plugin.setActions(List.of(
                ActionDescriptor.builder().name("action-1").build(),
                ActionDescriptor.builder().name("action-2").build()));
        return plugin;
    }

    List<Variable> variableList() {
        Variable notSet = Util.buildVariable("notSet", null, "default");
        Variable notSetAndMasked = Util.buildVariable("notSetAndMasked", null, "default");
        notSetAndMasked.setMasked(true);
        Variable setValue = Util.buildVariable("setValue", "value", "default");
        Variable setValueAndMasked = Util.buildVariable("setValueAndMasked", "value", "default");
        setValueAndMasked.setMasked(true);
        return List.of(notSet, notSetAndMasked, setValue, setValueAndMasked);
    }
}
