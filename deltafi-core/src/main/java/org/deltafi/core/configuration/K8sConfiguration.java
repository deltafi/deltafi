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
package org.deltafi.core.configuration;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.plugin.deployer.K8sDeployerService;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.plugin.deployer.credential.SecretCredentialProvider;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationConfigRepo;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationRepo;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationService;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryRepo;
import org.deltafi.core.snapshot.SystemSnapshotService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

/**
 * If the application is hosted in a K8S instance create the K8SDeployerService
 */
@Slf4j
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
    public DeployerService deployerService(DeltaFiProperties deltaFiProperties, KubernetesClient kubernetesClient, PluginImageRepositoryRepo imageRepositoryRepo, PluginCustomizationService pluginCustomizationService, PluginRegistryService pluginRegistryService, SystemSnapshotService systemSnapshotService) {
        return new K8sDeployerService(deltaFiProperties, kubernetesClient, imageRepositoryRepo, pluginCustomizationService, pluginRegistryService, systemSnapshotService);
    }

}
