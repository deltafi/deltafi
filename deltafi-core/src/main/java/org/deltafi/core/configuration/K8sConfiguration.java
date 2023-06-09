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
package org.deltafi.core.configuration;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.plugin.deployer.K8sDeployerService;
import org.deltafi.core.plugin.deployer.PodService;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.plugin.deployer.credential.SecretCredentialProvider;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationConfigRepo;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationRepo;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationService;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryService;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.snapshot.SystemSnapshotService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

/**
 * If the application is hosted in a K8S instance create the K8SDeployerService
 */
@Configuration
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
public class K8sConfiguration {

    @Bean
    public CredentialProvider credentialProvider(KubernetesClient kubernetesClient) {
        return new SecretCredentialProvider(kubernetesClient);
    }

    @Bean
    public PluginCustomizationService pluginCustomizationsFetchService(PluginCustomizationConfigRepo pluginCustomizationConfigRepo,
                                                                       PluginCustomizationRepo pluginCustomizationRepo,
                                                                       CredentialProvider credentialProvider,
                                                                       HttpClient httpClient) {
        return new PluginCustomizationService(pluginCustomizationConfigRepo, pluginCustomizationRepo, credentialProvider, httpClient);
    }

    @Bean
    public PodService podService(KubernetesClient kubernetesClient) {
        return new PodService(kubernetesClient);
    }

    @Bean
    public DeployerService deployerService(DeltaFiPropertiesService deltaFiPropertiesService, PluginImageRepositoryService pluginImageRepositoryService, KubernetesClient kubernetesClient, PodService podService, PluginCustomizationService pluginCustomizationService, PluginRegistryService pluginRegistryService, SystemSnapshotService systemSnapshotService, EventService eventService) {
        return new K8sDeployerService(deltaFiPropertiesService, pluginImageRepositoryService, kubernetesClient, podService, pluginCustomizationService, pluginRegistryService, systemSnapshotService, eventService);
    }

}
