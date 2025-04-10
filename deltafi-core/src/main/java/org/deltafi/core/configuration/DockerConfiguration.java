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
package org.deltafi.core.configuration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.plugin.deployer.DockerDeployerService;
import org.deltafi.core.plugin.deployer.EnvironmentVariableHelper;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.plugin.deployer.credential.EnvVarCredentialProvider;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.services.SystemSnapshotService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!kubernetes")
public class DockerConfiguration {

    @Bean
    public DockerClient dockerClient() {
        DockerClientConfig standard = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost("unix:///var/run/docker.sock").build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(standard.getDockerHost()).build();
        return DockerClientImpl.getInstance(standard, httpClient);
    }

    @Bean
    public CredentialProvider credentialProvider() {
        return new EnvVarCredentialProvider();
    }

    @Bean
    public DeployerService dockerDeployerService(DockerClient dockerClient, PluginService pluginService, EventService eventService,
                                                 EnvironmentVariableHelper environmentVariableHelper, DeltaFiPropertiesService deltaFiPropertiesService) {
        return new DockerDeployerService(dockerClient, pluginService, eventService, environmentVariableHelper, deltaFiPropertiesService);
    }
}
