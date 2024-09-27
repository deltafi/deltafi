/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.Plugin;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.PluginRegistration;
import org.deltafi.common.types.Variable;
import org.deltafi.core.repo.PluginRepository;
import org.deltafi.core.types.PluginEntity;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.Result;
import org.deltafi.core.util.Util;
import org.deltafi.core.validation.PluginValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PluginServiceTest {

    private static final PluginCoordinates PLUGIN_COORDINATES_1 = new PluginCoordinates("org.mock", "plugin-1", "1.0.0");
    private static final PluginCoordinates PLUGIN_COORDINATES_2 = new PluginCoordinates("org.mock", "plugin-2", "1.0.0");

    @Mock
    PluginRepository pluginRepository;

    @Mock
    PluginVariableService pluginVariableService;

    @Mock
    EgressFlowPlanService egressFlowPlanService;

    @Mock
    EgressFlowService egressFlowService;

    @Mock
    TransformFlowPlanService transformFlowPlanService;

    @Mock
    TransformFlowService transformFlowService;

    @Mock
    RestDataSourcePlanService restDataSourcePlanService;

    @Mock
    TimedDataSourcePlanService timedDataSourcePlanService;

    @Mock
    RestDataSourceService restDataSourceService;

    @Mock
    TimedDataSourceService timedDataSourceService;

    @Mock
    PluginValidator pluginValidator;

    @Mock
    CoreEventQueuePluginCleaner coreEventQueuePluginCleaner;

    PluginService pluginService;

    @Mock
    Environment environment;

    @Mock
    BuildProperties buildProperties;

    @BeforeEach
    public void setup() {
        List<PluginCleaner> cleaners = List.of(egressFlowService, transformFlowService, restDataSourceService,
                timedDataSourceService, pluginVariableService, coreEventQueuePluginCleaner);
        List<PluginUninstallCheck> checkers = List.of(egressFlowService, transformFlowService, restDataSourceService);

        pluginService = new PluginService(pluginRepository, pluginVariableService, buildProperties,
                egressFlowService, restDataSourceService, timedDataSourceService, transformFlowService,
                environment, pluginValidator, egressFlowPlanService, restDataSourcePlanService,
                timedDataSourcePlanService, transformFlowPlanService, checkers, cleaners);
    }

    @Test
    void addsPluginWithDependencies() {
        Mockito.when(pluginValidator.validate(Mockito.any())).thenReturn(List.of());

        PluginEntity plugin = new PluginEntity();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder().pluginCoordinates(plugin.getPluginCoordinates()).build();
        Result result = pluginService.register(pluginRegistration);

        assertTrue(result.isSuccess());
        ArgumentCaptor<PluginEntity> pluginArgumentCaptor = ArgumentCaptor.forClass(PluginEntity.class);
        Mockito.verify(pluginRepository).save(pluginArgumentCaptor.capture());
        assertEquals(plugin, pluginArgumentCaptor.getValue());
        Mockito.verify(egressFlowService).validateAllFlows();
        Mockito.verify(restDataSourceService).validateAllFlows();
        Mockito.verify(timedDataSourceService).validateAllFlows();
        Mockito.verify(transformFlowService).validateAllFlows();
    }

    @Test
    void addPluginMissingDependenciesReturnsErrors() {
        Mockito.when(pluginValidator.validate(Mockito.any())).thenReturn(List.of("error1", "error2"));

        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder().pluginCoordinates(plugin.getPluginCoordinates()).build();
        Result result = pluginService.register(pluginRegistration);

        assertFalse(result.isSuccess());
        assertEquals(2, result.getErrors().size());
        assertEquals("error1", result.getErrors().get(0));
        assertEquals("error2", result.getErrors().get(1));
        Mockito.verify(pluginRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void uninstallNotFound() {
        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.empty());
        List<String> errors = pluginService.canBeUninstalled(PLUGIN_COORDINATES_1);

        assertThat(errors).hasSize(1).contains("Plugin not found");
    }

    @Test
    void uninstallFlowRunning() {
        PluginEntity plugin1 = makePlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));
        Mockito.when(transformFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following ingress flows which are still running: mockIngress");

        List<String> errors = pluginService.canBeUninstalled(PLUGIN_COORDINATES_1);

        assertThat(errors).hasSize(1).contains("The plugin has created the following ingress flows which are still running: mockIngress");
    }

    @Test
    void uninstallIsADependency() throws JsonProcessingException {
        PluginEntity plugin1 = makePlugin();
        PluginEntity plugin2 = makeDependencyPlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));
        Mockito.when(pluginRepository.findPluginsWithDependency(PLUGIN_COORDINATES_1)).thenReturn(List.of(plugin1, plugin2));

        List<String> errors = pluginService.canBeUninstalled(PLUGIN_COORDINATES_1);

        assertThat(errors).hasSize(1).contains("The following plugins depend on this plugin: org.mock:plugin-1:1.0.0, org.mock:plugin-2:1.0.0");
    }

    @Test
    void uninstallRunningAndADependency() throws JsonProcessingException {
        PluginEntity plugin1 = makePlugin();
        PluginEntity plugin2 = makeDependencyPlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));
        Mockito.when(pluginRepository.findPluginsWithDependency(PLUGIN_COORDINATES_1)).thenReturn(List.of(plugin1, plugin2));
        Mockito.when(transformFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following ingress flows which are still running: mockIngress");
        Mockito.when(restDataSourceService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following enrich flows which are still running: mockEnrich");
        Mockito.when(egressFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following egress flows which are still running: mockEgress");

        List<String> errors = pluginService.canBeUninstalled(PLUGIN_COORDINATES_1);

        assertThat(errors).hasSize(4)
                .contains("The plugin has created the following ingress flows which are still running: mockIngress")
                .contains("The plugin has created the following enrich flows which are still running: mockEnrich")
                .contains("The plugin has created the following egress flows which are still running: mockEgress")
                .contains("The following plugins depend on this plugin: org.mock:plugin-1:1.0.0, org.mock:plugin-2:1.0.0");
    }

    @Test
    void uninstallSuccess() {
        PluginEntity plugin1 = makePlugin();

        Mockito.when(pluginRepository.findById(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));

        pluginService.uninstallPlugin(PLUGIN_COORDINATES_1);

        Mockito.verify(pluginRepository).deleteById(PLUGIN_COORDINATES_1);
        Mockito.verify(egressFlowService).cleanupFor(plugin1);
        Mockito.verify(pluginVariableService).cleanupFor(plugin1);
        Mockito.verify(coreEventQueuePluginCleaner).cleanupFor(plugin1);
    }

    @Test
    void testUpdateSnapshot() {
        PluginEntity one = makePlugin();
        PluginEntity two = makePlugin();
        two.setPluginCoordinates(PLUGIN_COORDINATES_2);
        SystemSnapshot systemSnapshot = new SystemSnapshot();

        Mockito.when(pluginRepository.findAll()).thenReturn(List.of(one, two));
        pluginService.updateSnapshot(systemSnapshot);

        assertThat(systemSnapshot.getInstalledPlugins()).hasSize(2).contains(PLUGIN_COORDINATES_1, PLUGIN_COORDINATES_2);
    }

    @Test
    void testResetFromSnapshot() {
        SystemSnapshot systemSnapshot = new SystemSnapshot();

        PluginEntity installedOnly = makePlugin();
        installedOnly.setPluginCoordinates(new PluginCoordinates("org.installed", "installed-plugin", "1.0.0"));
        PluginEntity newVersion = makePlugin();
        newVersion.setPluginCoordinates( new PluginCoordinates("org.mock", "plugin-2", "1.1.0"));


        PluginEntity inBoth = makePlugin();
        PluginCoordinates inSnapshotOnly = new PluginCoordinates("org.unique", "custom-plugin", "1.0.0");


        Mockito.when(pluginRepository.findAll()).thenReturn(List.of(installedOnly, newVersion, inBoth));

        systemSnapshot.setInstalledPlugins(Set.of(inBoth.getPluginCoordinates(), PLUGIN_COORDINATES_2, inSnapshotOnly));

        Result result = pluginService.resetFromSnapshot(systemSnapshot, true);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInfo()).hasSize(3)
                .contains("Installed plugin org.mock:plugin-2:1.1.0 was a different version at the time of the snapshot: org.mock:plugin-2:1.0.0")
                .contains("Plugin org.unique:custom-plugin:1.0.0 was installed at the time of the snapshot but is no longer installed")
                .contains("Installed plugin org.installed:installed-plugin:1.0.0 was not installed at the time of the snapshot");
    }

    @Test
    void testGetPluginsWithVariablesAsAdmin() {
        try (MockedStatic<DeltaFiUserService> userDetailsServiceMockedStatic = Mockito.mockStatic(DeltaFiUserService.class)) {
            testGetPluginsWithVariables(userDetailsServiceMockedStatic, true);
        }
    }

    @Test
    void testGetPluginsWithVariablesAsNonAdmin() {
        try (MockedStatic<DeltaFiUserService> userDetailsServiceMockedStatic = Mockito.mockStatic(DeltaFiUserService.class)) {
            testGetPluginsWithVariables(userDetailsServiceMockedStatic, false);
        }
    }

    private void testGetPluginsWithVariables(MockedStatic<DeltaFiUserService> userDetailsServiceMockedStatic, boolean isAdmin) {
        PluginEntity one = makeDependencyPlugin();
        PluginEntity two = makeDependencyPlugin();
        Variable variable = Util.buildNewVariable("setValue");
        one.setVariables(List.of(variable));
        Mockito.when(pluginRepository.findAll()).thenReturn(List.of(one, two));
        Mockito.when(pluginVariableService.getVariablesByPlugin(Mockito.any())).thenReturn(variableList());

        userDetailsServiceMockedStatic.when(DeltaFiUserService::currentUserCanViewMasked).thenReturn(isAdmin);
        List<PluginEntity> plugins = pluginService.getPluginsWithVariables();
        assertThat(plugins).hasSize(2);
        Consumer<Variable> checker = isAdmin ? this::verifyNoMaskedValues : this::verifyMaskedValues;
        plugins.stream().map(PluginEntity::getVariables).flatMap(Collection::stream).forEach(checker);
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

    private PluginEntity makeDependencyPlugin() {
        PluginEntity plugin = new PluginEntity();
        plugin.setPluginCoordinates(PLUGIN_COORDINATES_2);
        plugin.setActions(List.of(
                ActionDescriptor.builder().name("action-x").build(),
                ActionDescriptor.builder().name("action-y").build()));
        plugin.setDependencies(List.of(PLUGIN_COORDINATES_1));
        return plugin;
    }

    private PluginEntity makePlugin() {
        PluginEntity plugin = new PluginEntity();
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