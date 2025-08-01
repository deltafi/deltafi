/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.common.types.*;
import org.deltafi.common.types.integration.ExpectedDeltaFile;
import org.deltafi.common.types.integration.IntegrationTest;
import org.deltafi.common.types.integration.TestCaseIngress;
import org.deltafi.core.generated.types.SystemFlowPlans;
import org.deltafi.core.integration.IntegrationService;
import org.deltafi.core.repo.PluginRepository;
import org.deltafi.core.types.PluginEntity;
import org.deltafi.core.types.snapshot.PluginSnapshot;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.Result;
import org.deltafi.core.util.UtilService;
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

import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.core.services.PluginService.SYSTEM_PLUGIN_ID;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PluginServiceTest {

    private static final PluginCoordinates PLUGIN_COORDINATES_1 = new PluginCoordinates("org.mock", "plugin-1", "1.0.0");
    private static final PluginCoordinates PLUGIN_COORDINATES_2 = new PluginCoordinates("org.mock", "plugin-2", "1.0.0");
    private static final PluginSnapshot PLUGIN_1 = new PluginSnapshot(null, null, PLUGIN_COORDINATES_1);
    private static final PluginSnapshot PLUGIN_2 = new PluginSnapshot(null, null, PLUGIN_COORDINATES_2);

    @Mock
    PluginRepository pluginRepository;

    @Mock
    PluginVariableService pluginVariableService;

    @Mock
    DataSinkPlanService dataSinkPlanService;

    @Mock
    DataSinkService dataSinkService;

    @Mock
    TransformFlowPlanService transformFlowPlanService;

    @Mock
    TransformFlowService transformFlowService;

    @Mock
    RestDataSourcePlanService restDataSourcePlanService;

    @Mock
    TimedDataSourcePlanService timedDataSourcePlanService;

    @Mock
    OnErrorDataSourcePlanService onErrorDataSourcePlanService;

    @Mock
    OnErrorDataSourceService onErrorDataSourceService;

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

    @Mock
    IntegrationService integrationService;

    @BeforeEach
    void setup() {
        List<PluginCleaner> cleaners = List.of(dataSinkService, transformFlowService, restDataSourceService,
                timedDataSourceService, pluginVariableService, coreEventQueuePluginCleaner);
        List<PluginUninstallCheck> checkers = List.of(dataSinkService, transformFlowService, restDataSourceService);

        pluginService = new PluginService(pluginRepository, pluginVariableService, buildProperties,
                dataSinkService, restDataSourceService, timedDataSourceService, onErrorDataSourceService, transformFlowService,
                environment, pluginValidator, dataSinkPlanService, restDataSourcePlanService,
                timedDataSourcePlanService, onErrorDataSourcePlanService, transformFlowPlanService, checkers, cleaners);
    }

    @Test
    void addsPluginWithDependencies() {
        Mockito.when(pluginValidator.validate(Mockito.any())).thenReturn(List.of());

        PluginEntity plugin = new PluginEntity();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder().pluginCoordinates(plugin.getPluginCoordinates()).build();
        Result result = pluginService.register(pluginRegistration, integrationService);

        assertTrue(result.isSuccess());
        ArgumentCaptor<PluginEntity> pluginArgumentCaptor = ArgumentCaptor.forClass(PluginEntity.class);
        Mockito.verify(pluginRepository).save(pluginArgumentCaptor.capture());
        plugin.setRegistrationHash(PluginService.hashRegistration(pluginRegistration));
        assertEquals(plugin, pluginArgumentCaptor.getValue());
    }

    @Test
    void isRegistrationNewNotPreviouslyInstalled() {
        PluginEntity plugin = new PluginEntity();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder()
                .pluginCoordinates(plugin.getPluginCoordinates())
                .integrationTests(List.of(makeIntegrationTest()))
                .build();
        plugin.setRegistrationHash(PluginService.hashRegistration(pluginRegistration));

        Mockito.when(pluginRepository
                        .findByKeyGroupIdAndKeyArtifactIdAndVersion(
                                "group", "artifact", "1.0.0"))
                .thenReturn(Optional.empty());
        // plugin with these same coordinates not found
        assertTrue(pluginService.isRegistrationNew(pluginRegistration));
    }

    @Test
    void isRegistrationNewDifferentHash() {
        PluginEntity plugin = new PluginEntity();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder()
                .pluginCoordinates(plugin.getPluginCoordinates())
                .integrationTests(List.of(makeIntegrationTest()))
                .build();

        Mockito.when(pluginRepository
                        .findByKeyGroupIdAndKeyArtifactIdAndVersion(
                                "group", "artifact", "1.0.0"))
                .thenReturn(Optional.of(plugin));
        // plugin with these coordinate found, but previous registration had a different hash
        assertTrue(pluginService.isRegistrationNew(pluginRegistration));
    }

    @Test
    void isRegistrationNewSameHash() {
        PluginEntity plugin = new PluginEntity();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder()
                .pluginCoordinates(plugin.getPluginCoordinates())
                .integrationTests(List.of(makeIntegrationTest()))
                .build();
        plugin.setRegistrationHash(PluginService.hashRegistration(pluginRegistration));

        Mockito.when(pluginRepository
                        .findByKeyGroupIdAndKeyArtifactIdAndVersion(
                                "group", "artifact", "1.0.0"))
                .thenReturn(Optional.of(plugin));
        // plugin with these coordinate found, and previous registration had the same hash
        assertFalse(pluginService.isRegistrationNew(pluginRegistration));
    }

    @Test
    void testComputeHashDescriptionCheck() {
        String orig = hashPlugin("description", Collections.emptyList());
        String same = hashPlugin("description", Collections.emptyList());
        String different = hashPlugin("description!!", Collections.emptyList());

        assertThat(orig).isEqualTo(same).isNotEqualTo(different);
    }

    private RestDataSourcePlan makeRestDataSourcePlan(Map<String, String> annotations) {
         return new RestDataSourcePlan("rest", FlowType.REST_DATA_SOURCE, "desc", Collections.emptyMap(),
                new AnnotationConfig(annotations, Collections.emptyList(), null), "topic");
    }
    private String hashPlugin(String description, List<FlowPlan> flowPlans) {
        PluginEntity plugin = new PluginEntity();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder()
                .description(description)
                .pluginCoordinates(plugin.getPluginCoordinates())
                .flowPlans(flowPlans)
                .build();
        return PluginService.hashRegistration(pluginRegistration);
    }

    @Test
    void testComputeHashFlowCheck() {
        String orig = hashPlugin("desc",
                List.of(makeRestDataSourcePlan(Map.of("a", "b"))));
        String same = hashPlugin("desc",
                List.of(makeRestDataSourcePlan(Map.of("a", "b"))));
        String different = hashPlugin("desc",
                List.of(makeRestDataSourcePlan(Map.of("a", "Z"))));

        assertThat(orig).isEqualTo(same).isNotEqualTo(different);
    }

    @Test
    void hashRegistration() {
        PluginEntity plugin1 = new PluginEntity();
        plugin1.setPluginCoordinates(PLUGIN_COORDINATES_1);
        PluginRegistration registration1 = PluginRegistration.builder()
                .pluginCoordinates(plugin1.getPluginCoordinates())
                .description("description")
                .build();

        PluginEntity samePlugin1 = new PluginEntity();
        samePlugin1.setPluginCoordinates(PLUGIN_COORDINATES_1);
        PluginRegistration sameRegistration1 = PluginRegistration.builder()
                .pluginCoordinates(samePlugin1.getPluginCoordinates())
                .description("description")
                .build();

        assertEquals(PluginService.hashRegistration(registration1), PluginService.hashRegistration(sameRegistration1));

        PluginRegistration differentDescription = PluginRegistration.builder()
                .pluginCoordinates(samePlugin1.getPluginCoordinates())
                .description("description2")
                .build();

        assertNotEquals(PluginService.hashRegistration(registration1), PluginService.hashRegistration(differentDescription));
    }

    @Test
    void addPluginWithIntegrationTest() {
        Mockito.when(pluginValidator.validate(Mockito.any())).thenReturn(List.of());
        Mockito.when(integrationService.validate(Mockito.any())).thenReturn(List.of());

        PluginEntity plugin = new PluginEntity();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder()
                .pluginCoordinates(plugin.getPluginCoordinates())
                .integrationTests(List.of(makeIntegrationTest()))
                .build();
        Result result = pluginService.register(pluginRegistration, integrationService);

        assertTrue(result.isSuccess());
        ArgumentCaptor<PluginEntity> pluginArgumentCaptor = ArgumentCaptor.forClass(PluginEntity.class);
        Mockito.verify(pluginRepository).save(pluginArgumentCaptor.capture());
        plugin.setRegistrationHash(PluginService.hashRegistration(pluginRegistration));
        assertEquals(plugin, pluginArgumentCaptor.getValue());

        Mockito.verify(integrationService).save(Mockito.any());
    }

    @Test
    void addPluginMissingDependenciesReturnsErrors() {
        Mockito.when(pluginValidator.validate(Mockito.any())).thenReturn(List.of("error1", "error2"));

        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder().pluginCoordinates(plugin.getPluginCoordinates()).build();
        Result result = pluginService.register(pluginRegistration, integrationService);

        assertFalse(result.isSuccess());
        assertEquals(2, result.getErrors().size());
        assertEquals("error1", result.getErrors().get(0));
        assertEquals("error2", result.getErrors().get(1));
        Mockito.verify(pluginRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void addPluginHasIntegrationTestErrors() {
        Mockito.when(pluginValidator.validate(Mockito.any())).thenReturn(List.of("error1", "error2"));
        Mockito.when(integrationService.validate(Mockito.any())).thenReturn(List.of("error3", "error4"));

        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "artifact", "1.0.0"));
        PluginRegistration pluginRegistration = PluginRegistration.builder()
                .pluginCoordinates(plugin.getPluginCoordinates())
                .integrationTests(List.of(IntegrationTest.builder().name("name").build()))
                .build();
        Result result = pluginService.register(pluginRegistration, integrationService);

        assertFalse(result.isSuccess());
        assertEquals(4, result.getErrors().size());
        assertEquals("error1", result.getErrors().get(0));
        assertEquals("error2", result.getErrors().get(1));
        assertEquals("error3", result.getErrors().get(2));
        assertEquals("error4", result.getErrors().get(3));
        Mockito.verify(pluginRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void uninstallNotFound() {
        List<String> errors = pluginService.canBeUninstalled(null);
        assertThat(errors).hasSize(1).contains("Plugin not found");
    }

    @Test
    void uninstallFlowRunning() {
        PluginEntity plugin1 = makePlugin();
        Mockito.when(transformFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following ingress flows which are still running: mockIngress");

        List<String> errors = pluginService.canBeUninstalled(plugin1);

        assertThat(errors).hasSize(1).contains("The plugin has created the following ingress flows which are still running: mockIngress");
    }

    @Test
    void uninstallIsADependency() {
        PluginEntity plugin1 = makePlugin();
        PluginEntity plugin2 = makeDependencyPlugin();

        Mockito.when(pluginRepository.findPluginsWithDependency(PLUGIN_COORDINATES_1)).thenReturn(List.of(plugin1, plugin2));

        List<String> errors = pluginService.canBeUninstalled(plugin1);

        assertThat(errors).hasSize(1).contains("The following plugins depend on this plugin: org.mock:plugin-1:1.0.0, org.mock:plugin-2:1.0.0");
    }

    @Test
    void uninstallRunningAndADependency() {
        PluginEntity plugin1 = makePlugin();
        PluginEntity plugin2 = makeDependencyPlugin();

        Mockito.when(pluginRepository.findPluginsWithDependency(PLUGIN_COORDINATES_1)).thenReturn(List.of(plugin1, plugin2));
        Mockito.when(transformFlowService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following ingress flows which are still running: mockIngress");
        Mockito.when(restDataSourceService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following enrich flows which are still running: mockEnrich");
        Mockito.when(dataSinkService.uninstallBlockers(plugin1)).thenReturn("The plugin has created the following egress flows which are still running: mockEgress");

        List<String> errors = pluginService.canBeUninstalled(plugin1);

        assertThat(errors).hasSize(4)
                .contains("The plugin has created the following ingress flows which are still running: mockIngress")
                .contains("The plugin has created the following enrich flows which are still running: mockEnrich")
                .contains("The plugin has created the following egress flows which are still running: mockEgress")
                .contains("The following plugins depend on this plugin: org.mock:plugin-1:1.0.0, org.mock:plugin-2:1.0.0");
    }

    @Test
    void uninstallSuccess() {
        PluginEntity plugin1 = makePlugin();

        Mockito.when(pluginService.getPlugin(PLUGIN_COORDINATES_1)).thenReturn(Optional.of(plugin1));

        pluginService.uninstallPlugin(PLUGIN_COORDINATES_1);

        Mockito.verify(pluginRepository).delete(plugin1);
        Mockito.verify(dataSinkService).cleanupFor(plugin1);
        Mockito.verify(pluginVariableService).cleanupFor(plugin1);
        Mockito.verify(coreEventQueuePluginCleaner).cleanupFor(plugin1);
    }

    @Test
    void testUpdateSnapshot() {
        PluginEntity one = makePlugin();
        PluginEntity two = makePlugin();
        two.setPluginCoordinates(PLUGIN_COORDINATES_2);
        Snapshot snapshot = new Snapshot();

        Mockito.when(pluginRepository.findAll()).thenReturn(List.of(one, two));
        pluginService.updateSnapshot(snapshot);

        assertThat(snapshot.getPlugins()).hasSize(2).contains(PLUGIN_1, PLUGIN_2);
    }

    @Test
    void testSystemPluginRestore() {
        TransformFlowPlan keep = new TransformFlowPlan("keep", "");
        TransformFlowPlan replace = new TransformFlowPlan("replace", "");
        OnErrorDataSourcePlan onErrorKeep = new OnErrorDataSourcePlan("onErrorKeep", FlowType.ON_ERROR_DATA_SOURCE, "desc", Collections.emptyMap(), null, "topic", ".*Error.*", null, null, null, null, null);
        PluginEntity systemPlugin = new PluginEntity();
        systemPlugin.setFlowPlans(new ArrayList<>(List.of(keep, replace, onErrorKeep)));

        TransformFlowPlan snapshot = new TransformFlowPlan("snapshot", "");
        TransformFlowPlan replacement = new TransformFlowPlan("replace", "changed");
        OnErrorDataSourcePlan onErrorSnapshot = new OnErrorDataSourcePlan("onErrorSnapshot", FlowType.ON_ERROR_DATA_SOURCE, "desc", Collections.emptyMap(), null, "topic", ".*New.*", null, null, null, null, null);
        SystemFlowPlans systemFlowPlans = new SystemFlowPlans();
        systemFlowPlans.setTransformPlans(List.of(snapshot, replacement));
        systemFlowPlans.setOnErrorDataSources(List.of(onErrorSnapshot));

        Mockito.when(pluginRepository.findById(SYSTEM_PLUGIN_ID)).thenReturn(Optional.of(systemPlugin));
        pluginService.restoreSystemPlugin(systemFlowPlans, false);

        assertThat(systemPlugin.getFlowPlans()).hasSize(5).contains(keep, replacement, snapshot, onErrorKeep, onErrorSnapshot);
    }

    @Test
    void testGetSystemFlowPlansWithAllTypes() {
        RestDataSourcePlan restPlan = new RestDataSourcePlan("rest", FlowType.REST_DATA_SOURCE, "desc", "topic");
        TimedDataSourcePlan timedPlan = new TimedDataSourcePlan("timed", FlowType.TIMED_DATA_SOURCE, "desc", "topic", new ActionConfiguration("action", ActionType.TIMED_INGRESS, "type"), "*/5 * * * * *");
        OnErrorDataSourcePlan onErrorPlan = new OnErrorDataSourcePlan("onError", FlowType.ON_ERROR_DATA_SOURCE, "desc", Collections.emptyMap(), null, "error-topic", ".*Error.*", List.of(new ErrorSourceFilter(null, null, "action1", null)), null, null, null, null);
        TransformFlowPlan transformPlan = new TransformFlowPlan("transform", "desc");
        DataSinkPlan dataSinkPlan = new DataSinkPlan("dataSink", FlowType.DATA_SINK, "desc", new ActionConfiguration("egress", ActionType.EGRESS, "type"));

        PluginEntity systemPlugin = new PluginEntity();
        systemPlugin.setFlowPlans(List.of(restPlan, timedPlan, onErrorPlan, transformPlan, dataSinkPlan));

        Mockito.when(pluginRepository.findById(SYSTEM_PLUGIN_ID)).thenReturn(Optional.of(systemPlugin));
        SystemFlowPlans result = pluginService.getSystemFlowPlans();

        assertThat(result.getRestDataSources()).hasSize(1).contains(restPlan);
        assertThat(result.getTimedDataSources()).hasSize(1).contains(timedPlan);
        assertThat(result.getOnErrorDataSources()).hasSize(1).contains(onErrorPlan);
        assertThat(result.getTransformPlans()).hasSize(1).contains(transformPlan);
        assertThat(result.getDataSinkPlans()).hasSize(1).contains(dataSinkPlan);
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
        Variable variable = UtilService.buildNewVariable("setValue");
        one.setVariables(List.of(variable));
        Mockito.when(pluginRepository.findAll()).thenReturn(List.of(one, two));
        Mockito.when(pluginVariableService.getVariablesByPlugin(Mockito.any(), Mockito.eq(false))).thenReturn(variableList());

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
        Variable notSet = UtilService.buildVariable("notSet", null, "default");
        Variable notSetAndMasked = UtilService.buildVariable("notSetAndMasked", null, "default");
        notSetAndMasked.setMasked(true);
        Variable setValue = UtilService.buildVariable("setValue", "value", "default");
        Variable setValueAndMasked = UtilService.buildVariable("setValueAndMasked", "value", "default");
        setValueAndMasked.setMasked(true);
        return List.of(notSet, notSetAndMasked, setValue, setValueAndMasked);
    }

    IntegrationTest makeIntegrationTest() {
        TestCaseIngress ingress1 = TestCaseIngress.builder()
                .flow("ds")
                .ingressFileName("fn")
                .base64Encoded(false)
                .data("data")
                .build();
        PluginCoordinates pc = new PluginCoordinates("group", "art", "ANY");

        ExpectedDeltaFile e1 = ExpectedDeltaFile.builder()
                .stage(DeltaFileStage.COMPLETE)
                .childCount(3)
                .build();

        ExpectedDeltaFile e2 = ExpectedDeltaFile.builder()
                .stage(DeltaFileStage.ERROR)
                .childCount(0)
                .build();

        return IntegrationTest.builder()
                .name("test4")
                .description("desc")
                .inputs(List.of(ingress1))
                .plugins(List.of(pc))
                .dataSources(List.of("ds"))
                .transformationFlows(List.of("t"))
                .dataSinks(List.of("e"))
                .timeout("PT3M")
                .expectedDeltaFiles(List.of(e1, e2))
                .build();

    }
}
