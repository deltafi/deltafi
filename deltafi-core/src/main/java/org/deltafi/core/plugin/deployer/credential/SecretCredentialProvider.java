/**
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

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.AllArgsConstructor;
import org.deltafi.core.types.Result;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Retrieve a username and password from a k8s secret
 */
@AllArgsConstructor
public class SecretCredentialProvider implements CredentialProvider {

    private final KubernetesClient k8sClient;

    @Override
    public BasicCredentials getCredentials(String sourceName) {
        Assert.hasText(sourceName, "The secret name can not be null or empty");
        return extractBasicCredentials(k8sClient.secrets().withName(sourceName).get(), sourceName);
    }

    BasicCredentials extractBasicCredentials(Secret secret, String sourceName) {
        Assert.notNull(secret, "Secret named: " +  sourceName + " could not be found");

        String username = secret.getData().get("username");
        String password = secret.getData().get("password");

        Assert.notNull(username, "Secret named: " + sourceName + " did not contain a username");
        Assert.notNull(password, "Secret named: " + sourceName + " did not contain a password");

        return new BasicCredentials(decodeValue(username), decodeValue(password));
    }

    @Override
    public Result createCredentials(String sourceName, String username, String password) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
                .withName(sourceName);
        Secret secret = new SecretBuilder()
                .withMetadata(metaBuilder.build())
                .withType("Opaque")
                .withData(Map.of("username", encodeValue(username), "password", encodeValue(password)))
                .build();

        k8sClient.secrets().resource(secret).createOrReplace();

        return new Result();
    }

    private String decodeValue(String value) {
        return new String(Base64.getDecoder().decode(value)).trim();
    }

    private String encodeValue(String value) {
        return new String(Base64.getEncoder().encode(value.getBytes(StandardCharsets.UTF_8))).trim();
    }
}
