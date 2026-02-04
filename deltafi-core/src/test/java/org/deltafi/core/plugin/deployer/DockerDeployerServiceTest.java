/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Unit tests for DockerDeployerService.
// ABOUTME: Tests container removal behavior with mocked DockerClient.
package org.deltafi.core.plugin.deployer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.configuration.SslSecretNames;
import org.deltafi.core.types.RegistryAuth;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.PluginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerDeployerServiceTest {

    @Mock
    private DockerClient dockerClient;

    @Mock
    private PluginService pluginService;

    @Mock
    private EventService eventService;

    @Mock
    private SslSecretNames sslSecretNames;

    @Mock
    private EnvironmentVariableHelper environmentVariableHelper;

    private DockerDeployerService dockerDeployerService;

    @BeforeEach
    void setUp() {
        when(environmentVariableHelper.getEnvVars()).thenReturn(List.of());
        when(environmentVariableHelper.getDataDir()).thenReturn("/data");

        dockerDeployerService = new DockerDeployerService(
                dockerClient,
                pluginService,
                sslSecretNames,
                eventService,
                environmentVariableHelper,
                new MockDeltaFiPropertiesService()
        );
    }

    @Test
    void removePlugin_removesExistingContainer() {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn("container-id");

        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.withNameFilter(anySet())).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        when(dockerClient.stopContainerCmd(anyString())).thenReturn(stopCmd);

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd(anyString())).thenReturn(removeCmd);

        dockerDeployerService.removePlugin("registry.example.com/test-plugin:1.0.0");

        verify(dockerClient).stopContainerCmd("container-id");
        verify(dockerClient).removeContainerCmd("container-id");
    }

    @Test
    void removePlugin_usesCorrectNameFilterPattern() {
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.withNameFilter(anySet())).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of());

        dockerDeployerService.removePlugin("registry.example.com/my-plugin:2.0.0");

        ArgumentCaptor<Set<String>> filterCaptor = ArgumentCaptor.forClass(Set.class);
        verify(listContainersCmd).withNameFilter(filterCaptor.capture());

        assertThat(filterCaptor.getValue()).containsExactly("^/my-plugin$");
    }

    @Test
    void removePlugin_handlesNullImageName() {
        dockerDeployerService.removePlugin(null);

        verify(dockerClient, never()).listContainersCmd();
    }

    @Test
    void removePlugin_handlesNoExistingContainer() {
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.withNameFilter(anySet())).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of());

        dockerDeployerService.removePlugin("test-plugin:1.0.0");

        verify(dockerClient, never()).stopContainerCmd(anyString());
        verify(dockerClient, never()).removeContainerCmd(anyString());
    }

    @Test
    void getRunningPluginImage_returnsImageFromRunningContainer() {
        Container container = mock(Container.class);
        when(container.getState()).thenReturn("running");
        when(container.getImage()).thenReturn("registry.example.com/test-plugin:1.0.0");

        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.withNameFilter(anySet())).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        String runningImage = dockerDeployerService.getRunningPluginImage("registry.example.com/test-plugin:1.0.0");

        assertThat(runningImage).isEqualTo("registry.example.com/test-plugin:1.0.0");
    }

    @Test
    void getRunningPluginImage_returnsNullForStoppedContainer() {
        Container container = mock(Container.class);
        when(container.getState()).thenReturn("exited");

        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.withNameFilter(anySet())).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        String runningImage = dockerDeployerService.getRunningPluginImage("registry.example.com/test-plugin:1.0.0");

        assertThat(runningImage).isNull();
    }

    @Test
    void getRunningPluginImage_returnsNullForNoContainer() {
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.withNameFilter(anySet())).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of());

        String runningImage = dockerDeployerService.getRunningPluginImage("registry.example.com/test-plugin:1.0.0");

        assertThat(runningImage).isNull();
    }

    @Test
    void getRunningPluginImage_handlesNullImageName() {
        String runningImage = dockerDeployerService.getRunningPluginImage(null);

        assertThat(runningImage).isNull();
        verify(dockerClient, never()).listContainersCmd();
    }

    @Test
    void extractRegistry_handlesPrivateRegistryWithPort() {
        assertThat(dockerDeployerService.extractRegistry("registry.example.com:5000/myrepo/image:tag"))
                .isEqualTo("registry.example.com:5000");
    }

    @Test
    void extractRegistry_handlesPrivateRegistry() {
        assertThat(dockerDeployerService.extractRegistry("registry.example.com/myrepo/image:tag"))
                .isEqualTo("registry.example.com");
    }

    @Test
    void extractRegistry_handlesECRRegistry() {
        assertThat(dockerDeployerService.extractRegistry("123456789012.dkr.ecr.us-east-1.amazonaws.com/myrepo:v1.0.0"))
                .isEqualTo("123456789012.dkr.ecr.us-east-1.amazonaws.com");
    }

    @Test
    void extractRegistry_handlesGCRRegistry() {
        assertThat(dockerDeployerService.extractRegistry("gcr.io/myproject/myimage:latest"))
                .isEqualTo("gcr.io");
    }

    @Test
    void extractRegistry_handlesDockerHub() {
        assertThat(dockerDeployerService.extractRegistry("ubuntu:latest")).isNull();
        assertThat(dockerDeployerService.extractRegistry("nginx")).isNull();
        assertThat(dockerDeployerService.extractRegistry("library/ubuntu:20.04")).isNull();
    }

    @Test
    void extractRegistry_handlesDockerHubNamespace() {
        assertThat(dockerDeployerService.extractRegistry("myuser/myimage:tag")).isNull();
    }

    @Test
    void extractRegistry_handlesLocalhost() {
        assertThat(dockerDeployerService.extractRegistry("localhost:5000/myimage:tag"))
                .isEqualTo("localhost:5000");
        assertThat(dockerDeployerService.extractRegistry("localhost/myimage:tag"))
                .isEqualTo("localhost");
    }

    @Test
    void extractRegistry_handlesImageWithDigest() {
        assertThat(dockerDeployerService.extractRegistry(
                "registry.example.com/myrepo/image@sha256:abc123def456"))
                .isEqualTo("registry.example.com");
    }

    @Test
    void extractRegistry_handlesImageWithTagAndDigest() {
        assertThat(dockerDeployerService.extractRegistry(
                "registry.example.com/myrepo/image:v1.0.0@sha256:abc123def456"))
                .isEqualTo("registry.example.com");
    }

    @Test
    void extractRegistry_handlesNestedPaths() {
        assertThat(dockerDeployerService.extractRegistry(
                "registry.example.com/team/project/service:version"))
                .isEqualTo("registry.example.com");
    }

    @Test
    void extractRegistry_handlesNullAndEmptyStrings() {
        assertThat(dockerDeployerService.extractRegistry(null)).isNull();
        assertThat(dockerDeployerService.extractRegistry("")).isNull();
        assertThat(dockerDeployerService.extractRegistry("   ")).isNull();
    }

    @Test
    void findRegistryAuth_findsExactMatch() {
        Map<String, RegistryAuth> auths = new HashMap<>();
        auths.put("registry.example.com", new RegistryAuth("user1", "pass1", null, null, null));

        RegistryAuth result = dockerDeployerService.findRegistryAuth(auths, "registry.example.com");

        assertThat(result).isNotNull();
        assertThat(result.username()).isEqualTo("user1");
        assertThat(result.password()).isEqualTo("pass1");
    }

    @Test
    void findRegistryAuth_findsDockerHubVariations() {
        Map<String, RegistryAuth> auths = new HashMap<>();
        auths.put("index.docker.io/v1", new RegistryAuth("dockeruser", "dockerpass", null, null, null));

        // When registry is null (Docker Hub), should find it
        RegistryAuth result = dockerDeployerService.findRegistryAuth(auths, null);

        assertThat(result).isNotNull();
        assertThat(result.username()).isEqualTo("dockeruser");
    }

    @Test
    void findRegistryAuth_handlesRegistryWithPort() {
        Map<String, RegistryAuth> auths = new HashMap<>();
        auths.put("registry.example.com", new RegistryAuth("user1", "pass1", null, null, null));

        // Should match registry.example.com:443 with registry.example.com
        RegistryAuth result = dockerDeployerService.findRegistryAuth(auths, "registry.example.com:443");

        assertThat(result).isNotNull();
        assertThat(result.username()).isEqualTo("user1");
    }

    @Test
    void findRegistryAuth_handlesRegistryWithDefaultPort() {
        Map<String, RegistryAuth> auths = new HashMap<>();
        auths.put("registry.example.com:443", new RegistryAuth("user1", "pass1", null, null, null));

        // Should match registry.example.com with registry.example.com:443
        RegistryAuth result = dockerDeployerService.findRegistryAuth(auths, "registry.example.com");

        assertThat(result).isNotNull();
        assertThat(result.username()).isEqualTo("user1");
    }

    @Test
    void findRegistryAuth_returnsNullWhenNotFound() {
        Map<String, RegistryAuth> auths = new HashMap<>();
        auths.put("registry.example.com", new RegistryAuth("user1", "pass1", null, null, null));

        RegistryAuth result = dockerDeployerService.findRegistryAuth(auths, "other.registry.com");

        assertThat(result).isNull();
    }

    @Test
    void findRegistryAuth_handlesMultipleRegistries() {
        Map<String, RegistryAuth> auths = new HashMap<>();
        auths.put("registry1.example.com", new RegistryAuth("user1", "pass1", null, null, null));
        auths.put("registry2.example.com", new RegistryAuth("user2", "pass2", null, null, null));
        auths.put("registry3.example.com", new RegistryAuth("user3", "pass3", null, null, null));

        RegistryAuth result1 = dockerDeployerService.findRegistryAuth(auths, "registry1.example.com");
        assertThat(result1).isNotNull();
        assertThat(result1.username()).isEqualTo("user1");

        RegistryAuth result2 = dockerDeployerService.findRegistryAuth(auths, "registry2.example.com");
        assertThat(result2).isNotNull();
        assertThat(result2.username()).isEqualTo("user2");

        RegistryAuth result3 = dockerDeployerService.findRegistryAuth(auths, "registry3.example.com");
        assertThat(result3).isNotNull();
        assertThat(result3.username()).isEqualTo("user3");
    }

    // Tests for RegistryAuth.toAuthConfig
    @Test
    void registryAuth_toAuthConfig_prefersIdentityToken() {
        RegistryAuth auth = new RegistryAuth("user", "pass", null, "identity-token", "registry-token");

        var authConfig = auth.toAuthConfig("registry.example.com");

        // Should prefer identity token over other auth methods
        assertThat(authConfig).isNotNull();
        assertThat(authConfig.getIdentitytoken()).isEqualTo("identity-token");
        assertThat(authConfig.getRegistrytoken()).isNull();
    }

    @Test
    void registryAuth_toAuthConfig_usesRegistryTokenIfNoIdentityToken() {
        RegistryAuth auth = new RegistryAuth("user", "pass", null, null, "registry-token");

        var authConfig = auth.toAuthConfig("registry.example.com");

        // Should use registry token if no identity token
        assertThat(authConfig).isNotNull();
        assertThat(authConfig.getIdentitytoken()).isNull();
        assertThat(authConfig.getRegistrytoken()).isEqualTo("registry-token");
    }

    @Test
    void registryAuth_toAuthConfig_usesUsernamePasswordIfNoTokens() {
        RegistryAuth auth = new RegistryAuth("user", "pass", null, null, null);

        var authConfig = auth.toAuthConfig("registry.example.com");

        // Should fall back to username/password if no tokens
        assertThat(authConfig).isNotNull();
        assertThat(authConfig.getUsername()).isEqualTo("user");
        assertThat(authConfig.getPassword()).isEqualTo("pass");
    }

    @Test
    void registryAuth_toAuthConfig_returnsNullIfNoCredentials() {
        RegistryAuth auth = new RegistryAuth(null, null, null, null, null);

        var authConfig = auth.toAuthConfig("registry.example.com");

        // Should return null if no valid credentials
        assertThat(authConfig).isNull();
    }
}
