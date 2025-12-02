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
package org.deltafi.core.services;

import org.deltafi.core.configuration.SslSecretNames;
import org.deltafi.core.types.KeyCertPair;
import org.deltafi.core.types.SslInfo;
import org.deltafi.core.types.SslSettings;

import java.util.*;

public interface SslConfigService {

    String SECRET_NAMED = "Secret named: ";
    String TLS_KEY = "tls.key";
    String TLS_CRT = "tls.crt";
    String KEY_PASSPHRASE = "keyPassphrase";
    String CA_CRT = "ca.crt";

    /**
     * Get the KeyCertPair in the given secret
     * @param secretName name of the secret to retrieve
     * @return the KeyCertPair
     */
    SslInfo getKeyCert(String secretName);

    /**
     * Get the KeyCertPair in the given secret
     * @param secretName name of the secret to retrieve
     * @param required throw an Exception if the secret is required but missing
     * @return the KeyCertPair
     */
    SslInfo getKeyCert(String secretName, boolean required);

    /**
     * Save a key/cert pair into the system. This will replace
     * @param secretName determines where the key/cert pair will be stored and retrieved from
     * @param keyCertPair key/cert contents
     * @return the saved value
     */
    SslInfo saveKeyCert(String secretName, KeyCertPair keyCertPair);

    /**
     * Delete the key/cert pair in the given secret
     * @param secretName name of the secret to delete
     * @return the deleted KeyCertPair
     */
    SslInfo deleteKeyCert(String secretName);

    /**
     * Add one or more cert entries to the existing ca-chain
     * @param certs info to append to the ca-chain
     * @return the ca-chain with the appended values
     */
    String appendToCaChain(String certs);

    /**
     * Load all the certs into the ca-chain. Any existing
     * entries will be replaced
     * @param certs list of certs to save as the ca-chain
     * @return the ca-chain with the new values
     */
    String saveCaChain(String certs);

    /**
     * Get the full SSL picture including server keys, plugin keys and ca-chain
     * @return running SslSettings including the active ca-chain and list of key/certs
     */
    SslSettings getSslSettings();

    /**
     * Get the key passphrase for the key used by plugins
     * @return the keyphrase or null if it is not set
     */
    String getPluginKeyPassphrase();

    default Map<String, SslInfo> getSslInfoMap(SslSecretNames sslSecretNames) {
        Map<String, SslInfo> keyCertPairs = new HashMap<>();

        keyCertPairs.computeIfAbsent(sslSecretNames.ingressSsl(), this::newSslInfo).usedBy().add("nginx-ingress");
        keyCertPairs.computeIfAbsent(sslSecretNames.coreSsl(), this::newSslInfo).usedBy().addAll(List.of("core-scheduler", "core-worker"));
        keyCertPairs.computeIfAbsent(sslSecretNames.pluginsSsl(), this::newSslInfo).usedBy().add("plugins");
        keyCertPairs.computeIfAbsent(sslSecretNames.entityResolverSsl(), this::newSslInfo).usedBy().add("entity-resolver");

        return keyCertPairs;
    }

    default SslInfo newSslInfo(String secretName) {
        return Optional.ofNullable(getKeyCert(secretName, false))
                .orElseGet(() -> SslInfo.builder().secretName(secretName).usedBy(new HashSet<>()).build());
    }

}
