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
package org.deltafi.core.plugin.deployer;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.*;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.assertj.core.api.Assertions;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.types.PluginEntity;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.snapshot.PluginSnapshot;
import org.deltafi.core.types.snapshot.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class K8sDeployerServiceTest {

    private static final InstallDetails INSTALL_DETAILS = InstallDetails.from("docker.io/plugin:1.0.0", "docker-cred");
    private static final PluginCoordinates SYSTEM = new PluginCoordinates(PluginService.SYSTEM_PLUGIN_GROUP_ID, PluginService.SYSTEM_PLUGIN_ARTIFACT_ID, "1.0.0");
    private static final PluginCoordinates CORE_ACTIONS = new PluginCoordinates("org.deltafi", "deltafi-core-actions", "1.0.0");

    final K8sDeployerService k8sDeployerService;
    final KubernetesClient k8sClient;
    final PluginService pluginService;

    K8sDeployerServiceTest(@Mock KubernetesClient k8sClient, @Mock PodService podService, @Mock PluginService pluginService, @Mock EventService eventService) {
        this.k8sDeployerService = new K8sDeployerService(new MockDeltaFiPropertiesService(), k8sClient, podService, pluginService, eventService);
        this.k8sClient = k8sClient;
        this.pluginService = pluginService;
    }

    @BeforeEach
    void setDeploymentTemplate() {
        k8sDeployerService.setBaseDeployment(new ClassPathResource("plugins/deployer/deployment-template.yaml"));
    }

    @Test
    void testCreateDeployment() throws IOException {
        Deployment deployment = k8sDeployerService.buildDeployment(INSTALL_DETAILS);

        Assertions.assertThat(deployment).isEqualTo(expectedDeployment());
    }

    @Test
    void testPreserveValuesIfUpgrade() throws IOException {
        Deployment withReplicas = expectedDeployment();
        withReplicas.getSpec().setReplicas(2);

        Deployment deployment = k8sDeployerService.buildDeployment(INSTALL_DETAILS);

        Assertions.assertThat(deployment.getSpec().getReplicas()).isNull();
        k8sDeployerService.preserveValues(deployment, withReplicas);
        Assertions.assertThat(deployment.getSpec().getReplicas()).isEqualTo(2);
    }

    @Test
    void testResetFromSnapshot_noPlugins() {
        Snapshot snapshot = new Snapshot();
        k8sDeployerService.resetFromSnapshot(snapshot, true);
        Mockito.verifyNoInteractions(pluginService);
    }

    @Test
    void testResetFromSnapshot_SkipCoreAndSystem() {
        PluginEntity system = new PluginEntity();
        system.setPluginCoordinates(SYSTEM);

        PluginEntity coreActions = new PluginEntity();
        coreActions.setPluginCoordinates(CORE_ACTIONS);

        PluginEntity plugin = new PluginEntity();
        plugin.setPluginCoordinates(new PluginCoordinates("org.plugin", "plugin", "1.0.0"));

        Mockito.when(pluginService.getPlugins()).thenReturn(List.of(system, coreActions, plugin));

        k8sDeployerService.resetFromSnapshot(snapshot(), true);
        Mockito.verify(pluginService).getPlugins();
        // Async model: marks for removal instead of immediate uninstall
        Mockito.verify(pluginService).markForRemoval(plugin.getPluginCoordinates());
        // Creates pending plugins for all snapshot plugins (excluding system/core-actions which have null imageName)
        Mockito.verify(pluginService, Mockito.times(2)).createPendingPlugin(Mockito.anyString(), Mockito.any());
        Mockito.verifyNoMoreInteractions(pluginService);
    }

    @Test
    void testResetFromSnapshot_multiplePlugins() {
        Mockito.when(pluginService.getPlugins()).thenReturn(List.of());

        Result result = k8sDeployerService.resetFromSnapshot(snapshot(), true);
        Assertions.assertThat(result.isSuccess()).isTrue();

        // Async model: creates pending plugins for reconciliation service to handle
        // 2 non-system plugins in the snapshot (plugin1 and plugin2)
        Mockito.verify(pluginService, Mockito.times(2)).createPendingPlugin(Mockito.anyString(), Mockito.any());
    }

    Deployment expectedDeployment() {
        try {
            return Serialization.unmarshal(new ClassPathResource("plugins/deployer/expected-deployment.yaml").getInputStream(), Deployment.class);
        } catch (IOException exception) {
            Assertions.fail("Could not read the file", exception);
            return null;
        }
    }

    private Snapshot snapshot() {
        Snapshot snapshot = new Snapshot();
        PluginSnapshot system = new PluginSnapshot(null, null, SYSTEM);
        PluginSnapshot coreActions = new PluginSnapshot("deltafi-core-actions", null, CORE_ACTIONS);
        PluginSnapshot plugin1 = new PluginSnapshot("image1", "docker-secret", new PluginCoordinates("org.test", "plugin1", "1.0.0"));
        PluginSnapshot plugin2 = new PluginSnapshot("image2", null, new PluginCoordinates("org.test", "plugin2", "1.0.0"));
        snapshot.setPlugins(List.of(system, coreActions, plugin1, plugin2));
        return snapshot;
    }
}