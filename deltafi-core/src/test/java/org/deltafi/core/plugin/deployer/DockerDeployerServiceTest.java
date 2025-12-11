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
// ABOUTME: Unit tests for DockerDeployerService.
// ABOUTME: Tests container removal behavior with mocked DockerClient.
package org.deltafi.core.plugin.deployer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.configuration.SslSecretNames;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.PluginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        when(listContainersCmd.withNameFilter(any(Set.class))).thenReturn(listContainersCmd);
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
        when(listContainersCmd.withNameFilter(any(Set.class))).thenReturn(listContainersCmd);
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
        when(listContainersCmd.withNameFilter(any(Set.class))).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of());

        dockerDeployerService.removePlugin("test-plugin:1.0.0");

        verify(dockerClient, never()).stopContainerCmd(anyString());
        verify(dockerClient, never()).removeContainerCmd(anyString());
    }
}
