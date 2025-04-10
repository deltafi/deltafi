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

import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
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
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class K8sDeployerServiceTest {

    private static final InstallDetails INSTALL_DETAILS = InstallDetails.from("docker.io/plugin:1.0.0", "docker-cred");
    private static final PluginCoordinates SYSTEM = new PluginCoordinates(PluginService.SYSTEM_PLUGIN_GROUP_ID, PluginService.SYSTEM_PLUGIN_ARTIFACT_ID, "1.0.0");
    private static final PluginCoordinates CORE_ACTIONS = new PluginCoordinates("org.deltafi", "deltafi-core-actions", "1.0.0");

    @Mock
    private NonNamespaceOperation<Deployment, DeploymentList, Resource<Deployment>> deploymentOperation;

    @Mock
    private MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deploymentMixedOp;

    @Mock
    private AppsAPIGroupDSL appsAPIGroupDSL;

    @Mock
    private RollableScalableResource<Deployment> deploymentResource;

    @Mock
    private NamespaceableResource<Deployment> namespaceableResource;

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

        setupK8Mocks();
        Mockito.when(deploymentResource.delete()).thenReturn(Collections.singletonList(new StatusDetails()));
        k8sDeployerService.resetFromSnapshot(snapshot(), true);
        Mockito.verify(pluginService).getPlugins();
        Mockito.verify(pluginService).uninstallPlugin(plugin.getPluginCoordinates());

        // only one call should be made to uninstall
        Mockito.verifyNoMoreInteractions(pluginService);
    }

    @Test
    void testResetFromSnapshot_multiplePlugins() {
        setupK8Mocks();
        Mockito.when(deploymentResource.get()).thenReturn(null);
        Mockito.when(k8sClient.resource(Mockito.any(Deployment.class))).thenReturn(namespaceableResource);
        Mockito.when(namespaceableResource.serverSideApply()).thenReturn(new Deployment());

        Result result = k8sDeployerService.resetFromSnapshot(snapshot(), true);
        Assertions.assertThat(result.isSuccess()).isTrue();

        // verify two deployments were applied
        Mockito.verify(namespaceableResource, Mockito.times(2)).serverSideApply();
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
        PluginSnapshot plugin1 = new PluginSnapshot("image1", "docker-secret", new PluginCoordinates());
        PluginSnapshot plugin2 = new PluginSnapshot("image2", null, new PluginCoordinates());
        snapshot.setPlugins(List.of(system, coreActions, plugin1, plugin2));
        return snapshot;
    }

    private void setupK8Mocks() {
        Mockito.when(k8sClient.apps()).thenReturn(appsAPIGroupDSL);
        Mockito.when(appsAPIGroupDSL.deployments()).thenReturn(deploymentMixedOp);
        Mockito.when(deploymentMixedOp.withName(Mockito.anyString())).thenReturn(deploymentResource);
    }
}