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
package org.deltafi.core.plugin.deployer.credential;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Map;


class SecretCredentialProviderTest {

    final KubernetesClient k8sClient;
    final SecretCredentialProvider secretCredentialProvider;

    @Mock @SuppressWarnings("unchecked")
    MixedOperation<Secret, SecretList, Resource<Secret>> secretsOperation = Mockito.mock(MixedOperation.class);
    @Mock @SuppressWarnings("unchecked")
    Resource<Secret> secretResource = Mockito.mock(Resource.class);

    private SecretCredentialProviderTest() {
        k8sClient = Mockito.mock(KubernetesClient.class);
        secretCredentialProvider = new SecretCredentialProvider(k8sClient, null, null);
    }

    @Test
    void getCredentials() {
        Map<String, String> secret = Map.of("username", "dXNlcm5hbWU=", "password", "cGFzc3dvcmQ=");
        BasicCredentials basicCredentials = secretCredentialProvider.extractBasicCredentials(secret, "my-secret");
        Assertions.assertThat(basicCredentials.getUsername()).isEqualTo("username");
        Assertions.assertThat(basicCredentials.getPassword()).isEqualTo("password");
    }

    @Test
    void getCredentials_nullSecret() {
        Mockito.when(k8sClient.secrets()).thenReturn(secretsOperation);
        Mockito.when(secretsOperation.withName("my-secret")).thenReturn(secretResource);
        Mockito.when(secretResource.get()).thenReturn(null);
        Assertions.assertThatThrownBy(() -> secretCredentialProvider.getCredentials( "my-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Secret named: my-secret could not be found");
    }

    @Test
    void getCredentials_noUsername() {
        Map<String, String> secret = Map.of("name", "name", "password", "password");
        Assertions.assertThatThrownBy(() -> secretCredentialProvider.extractBasicCredentials(secret, "my-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Secret named: my-secret did not contain a username");
    }

    @Test
    void getCredentials_noPassword() {
        Map<String, String> secret = Map.of("username", "name");
        Assertions.assertThatThrownBy(() -> secretCredentialProvider.extractBasicCredentials(secret, "my-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Secret named: my-secret did not contain a password");
    }

}