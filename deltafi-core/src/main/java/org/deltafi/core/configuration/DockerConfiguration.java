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
import org.deltafi.common.ssl.SslContextProvider;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.plugin.deployer.DockerDeployerService;
import org.deltafi.core.plugin.deployer.EnvironmentVariableHelper;
import org.deltafi.core.plugin.deployer.credential.ComposeSslConfigService;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.plugin.deployer.credential.EnvVarCredentialProvider;
import org.deltafi.core.services.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("!kubernetes")
public class DockerConfiguration {

    @Bean
    public DockerClient dockerClient(SslContextProvider sslContextProvider) {
        DockerClientConfig standard = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost("unix:///var/run/docker.sock").build();
        ApacheDockerHttpClient.Builder clientBuilder = new ApacheDockerHttpClient.Builder().dockerHost(standard.getDockerHost());
        if (sslContextProvider != null && sslContextProvider.isConfigured()) {
            clientBuilder.sslConfig(sslContextProvider::createSslContext);
        }

        return DockerClientImpl.getInstance(standard, clientBuilder.build());
    }

    @Bean
    public CredentialProvider credentialProvider() {
        return new EnvVarCredentialProvider();
    }

    @Bean
    public SslConfigService sslConfigService(CertificateInfoService certificateInfoService, SslSecretNames sslSecretNames) {
        return new ComposeSslConfigService(certificateInfoService, sslSecretNames);
    }

    @Bean
    public DeployerService dockerDeployerService(DockerClient dockerClient, PluginService pluginService, SslSecretNames sslSecretNames, EventService eventService,
                                                 EnvironmentVariableHelper environmentVariableHelper, DeltaFiPropertiesService deltaFiPropertiesService, Environment environment) {
        return new DockerDeployerService(dockerClient, pluginService, sslSecretNames, eventService, environmentVariableHelper, deltaFiPropertiesService, environment);
    }
}
