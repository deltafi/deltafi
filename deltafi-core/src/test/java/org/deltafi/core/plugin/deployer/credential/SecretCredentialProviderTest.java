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
package org.deltafi.core.plugin.deployer.credential;

import io.fabric8.kubernetes.api.model.Secret;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;


class SecretCredentialProviderTest {

    final SecretCredentialProvider secretCredentialProvider = new SecretCredentialProvider(null);

    @Test
    void getCredentials() {
        Secret secret = buildSecret(Map.of("username", "dXNlcm5hbWU=", "password", "cGFzc3dvcmQ="));
        BasicCredentials basicCredentials = secretCredentialProvider.extractBasicCredentials(secret, "my-secret");
        Assertions.assertThat(basicCredentials.getUsername()).isEqualTo("username");
        Assertions.assertThat(basicCredentials.getPassword()).isEqualTo("password");
    }

    @Test
    void getCredentials_nullSecret() {
        Assertions.assertThatThrownBy(() -> secretCredentialProvider.extractBasicCredentials(null, "my-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Secret named: my-secret could not be found");
    }

    @Test
    void getCredentials_noUsername() {
        Secret secret = buildSecret(Map.of("name", "name", "password", "password"));
        Assertions.assertThatThrownBy(() -> secretCredentialProvider.extractBasicCredentials(secret, "my-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Secret named: my-secret did not contain a username");
    }

    @Test
    void getCredentials_noPassworc() {
        Secret secret = buildSecret(Map.of("username", "name"));
        Assertions.assertThatThrownBy(() -> secretCredentialProvider.extractBasicCredentials(secret, "my-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Secret named: my-secret did not contain a password");
    }

    Secret buildSecret(Map<String, String> data) {
        Secret secret = new Secret();
        secret.setData(data);
        return secret;
    }

}