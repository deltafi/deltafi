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

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.configuration.SslSecretNames;
import org.deltafi.core.services.CertificateInfoService;
import org.deltafi.core.services.SslConfigService;
import org.deltafi.core.types.KeyCertPair;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.SslInfo;
import org.deltafi.core.types.SslSettings;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Secret management in K8S
 */
@RequiredArgsConstructor
public class SecretCredentialProvider implements CredentialProvider, SslConfigService {
    public static final String SSL_SECRET = "ssl-secret";
    public static final String DELTAFI = "deltafi";
    private final KubernetesClient k8sClient;
    private final CertificateInfoService certificateInfoService;
    private final SslSecretNames sslSecretNames;

    @Override
    public BasicCredentials getCredentials(String sourceName) {
        Assert.hasText(sourceName, "The secret name can not be null or empty");
        return extractBasicCredentials(getRequiredSecret(sourceName), sourceName);
    }

    BasicCredentials extractBasicCredentials(Map<String, String> decodedValues, String sourceName) {
        String username = decodedValues.get("username");
        String password = decodedValues.get("password");

        Assert.notNull(username, SECRET_NAMED + sourceName + " did not contain a username");
        Assert.notNull(password, SECRET_NAMED + sourceName + " did not contain a password");

        return new BasicCredentials(decodeValue(username), decodeValue(password));
    }

    @Override
    public Result createCredentials(String sourceName, String username, String password) {
        upsertSecret(sourceName, "Opaque", Map.of("username", username, "password", password), "basic-credential");
        return new Result();
    }

    /**
     * Get the KeyCertPair in the given secret
     *
     * @param secretName name of the secret to retrieve
     * @return the KeyCertPair
     */
    @Override
    public SslInfo getKeyCert(String secretName) {
        return getKeyCert(secretName, true);
    }

    /**
     * Get the KeyCertPair in the given secret
     *
     * @param secretName name of the secret to retrieve
     * @param required   throw an Exception if the secret is required but missing
     * @return the KeyCertPair
     */
    @Override
    public SslInfo getKeyCert(String secretName, boolean required) {
        Map<String, String> data = getSecret(secretName, required);
        KeyCertPair keyCertPair = new KeyCertPair(data.get(TLS_KEY), data.get(TLS_CRT), data.get(KEY_PASSPHRASE));
        return certificateInfoService.getSslInfo(secretName, keyCertPair);
    }

    /**
     * Save a key/cert pair into the system. This will replace
     *
     * @param secretName  determines where the key/cert pair will be stored and retrieved from
     * @param keyCertPair key/cert contents
     * @return the saved value
     */
    @Override
    public SslInfo saveKeyCert(String secretName, KeyCertPair keyCertPair) {
        Map<String, String> data = new HashMap<>();
        data.put(TLS_KEY, keyCertPair.key());
        data.put(TLS_CRT, keyCertPair.cert());
        if (StringUtils.isNotBlank(secretName)) {
            data.put(KEY_PASSPHRASE, secretName);
        }
        upsertSecret(secretName, "kubernetes.io/tls", data, SSL_SECRET);
        return certificateInfoService.getSslInfo(secretName, keyCertPair);
    }

    /**
     * Delete the key/cert pair in the given secret
     *
     * @param secretName name of the secret to delete
     * @return the deleted KeyCertPair
     */
    @Override
    public SslInfo deleteKeyCert(String secretName) {
        Secret secret = k8sClient.secrets().inNamespace(k8sClient.getNamespace())
                .withName(secretName)
                .get();

        if (secret == null) {
            throw new IllegalStateException("Secret " + secretName + " not found");
        }

        String label = Optional.ofNullable(secret.getMetadata())
                .map(ObjectMeta::getLabels).orElseGet(Map::of)
                .get(DELTAFI);

        if (!SSL_SECRET.equals(label)) {
            throw new IllegalStateException("Secret " + secretName + " was not created by DeltaFi, it must be manually deleted");
        }

        SslInfo sslInfo = sslInfo(secret);
        k8sClient.secrets().resource(secret).delete();
        return sslInfo;
    }

    /**
     * Add one or more cert entries to the existing ca-chain
     *
     * @param certs info to append to the ca-chain
     * @return the ca-chain with the appended values
     */
    @Override
    public String appendToCaChain(String certs) {
        String caChain = getCaChain();
        String fullChain = StringUtils.isNotBlank(caChain) ? caChain + "\n" + certs : certs;
        return saveCaChain(fullChain);
    }

    /**
     * Load all the certs into the ca-chain. Any existing
     * entries will be replaced
     *
     * @param certs list of certs to save as the ca-chain
     * @return the ca-chain with the new values
     */
    @Override
    public String saveCaChain(String certs) {
        upsertSecret(sslSecretNames.caChain(), "Opaque", Map.of(CA_CRT, certs), "ca-chain");
        return certs;
    }

    /**
     * Get the full SSL picture including server keys, plugin keys and ca-chain
     * Include details
     *
     * @return running SslInfo including the active ca-chain and of key/certs
     */
    @Override
    public SslSettings getSslSettings() {
        Map<String, SslInfo> keyCertPairs = getSslInfoMap(sslSecretNames);

        k8sClient.secrets().inNamespace(k8sClient.getNamespace())
                .withLabel(DELTAFI, SSL_SECRET).list().getItems()
                .forEach(secret -> addSslInfo(keyCertPairs, secret));

        return new SslSettings(keyCertPairs.values(), getCaChain());
    }

    @Override
    public String getPluginKeyPassphrase() {
        SslInfo sslInfo = getKeyCert(sslSecretNames.pluginsSsl(), false);
        return sslInfo != null ? sslInfo.keyPassphrase() : null;
    }

    private void addSslInfo(Map<String, SslInfo> sslInfoMap, Secret secret) {
        String secretName = secret.getMetadata().getName();
        if (sslInfoMap.containsKey(secretName)) {
            return;
        }

        sslInfoMap.put(secretName, sslInfo(secret));
    }

    private SslInfo sslInfo(Secret secret) {
        String secretName = secret.getMetadata().getName();
        Map<String, String> data = decodeData(secret);
        KeyCertPair keyCertPair = new KeyCertPair(data.get(TLS_KEY), data.get(TLS_CRT), data.get(KEY_PASSPHRASE));
        return certificateInfoService.getSslInfo(secretName, keyCertPair);
    }

    private String getCaChain() {
        Map<String, String> data = getOptionalSecret(sslSecretNames.caChain());
        return data.get(CA_CRT);
    }

    private void upsertSecret(String sourceName, String type, Map<String, String> rawData, String label) {
        Secret current = k8sClient.secrets().inNamespace(k8sClient.getNamespace()).withName(sourceName).get();
        if (current != null) {
            current.setData(encodeData(rawData));
            k8sClient.secrets().resource(current).update();
            return;
        }

        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
                .withNamespace(k8sClient.getNamespace())
                .withName(sourceName);
        if (StringUtils.isNotBlank(label)) {
            metaBuilder.withLabels(Map.of(DELTAFI, label));
        }
        Secret secret = new SecretBuilder()
                .withMetadata(metaBuilder.build())
                .withType(type)
                .withData(encodeData(rawData))
                .build();

        k8sClient.secrets().resource(secret).create();
    }

    private Map<String, String> getRequiredSecret(String secretName) {
        return getSecret(secretName, true);
    }

    private Map<String, String> getOptionalSecret(String secretName) {
        return getSecret(secretName, false);
    }

    private Map<String, String> getSecret(String secretName, boolean requiredSecret) {
        Secret secret = getSecret(secretName);
        if (requiredSecret) {
            Assert.notNull(secret, SECRET_NAMED + secretName + " could not be found");
        }
        return decodeData(secret);
    }

    private Secret getSecret(String secretName) {
        Assert.hasText(secretName, "The secret name can not be null or empty");
        return k8sClient.secrets().withName(secretName).get();
    }

    private Map<String, String> encodeData(Map<String, String> rawData) {
        return rawData != null ?
                rawData.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, value -> encodeValue(value.getValue())))
                : Map.of();
    }

    private Map<String, String> decodeData(Secret secret) {
        return Optional.ofNullable(secret)
                        .map(Secret::getData).orElse(Map.of())
                        .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, value -> decodeValue(value.getValue())));
    }

    private String decodeValue(String value) {
        return value != null ? new String(Base64.getDecoder().decode(value)).trim() : null;
    }

    private String encodeValue(String value) {
        return new String(Base64.getEncoder().encode(value.getBytes(StandardCharsets.UTF_8))).trim();
    }
}
