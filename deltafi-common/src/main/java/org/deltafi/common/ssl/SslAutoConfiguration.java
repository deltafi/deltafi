/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.ssl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.PemSslBundleProperties;
import org.springframework.boot.autoconfigure.ssl.PemSslBundleProperties.Store;
import org.springframework.context.annotation.Bean;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@AutoConfiguration
public class SslAutoConfiguration {

    @Bean
    public SslContextProvider sslContextProvider(
            @Value("${KEY_PATH:/certs/tls.key}") String keyPath,
            @Value("${CERT_PATH:/certs/tls.crt}") String certPath,
            @Value("${CA_CHAIN_PATH:/certs/ca.crt}") String caChainPath,
            @Value("${SSL_PROTOCOL:TLSv1.2}") String protocol,
            @Value("${KEY_PASSWORD:null}") String keyPass) {

        PemSslBundleProperties properties = new PemSslBundleProperties();
        if (filesExist(keyPath, certPath, caChainPath)) {
            log.info("Configuring the SSLContextProvider from key: '{}', certificate: '{}' and ca chain: '{}' with a protocol of '{}'", keyPath, certPath, caChainPath, protocol);
            properties.setProtocol(protocol);
            properties.getKey().setAlias("ssl");

            Store keyStore = properties.getKeystore();
            keyStore.setPrivateKey(keyPath);
            keyStore.setCertificate(certPath);
            keyStore.setPrivateKeyPassword(keyPass);

            properties.getTruststore().setCertificate(caChainPath);
        } else {
            log.info("Skipping SSL Setup - key and cert files were not found");
        }

        return new SslContextProvider(properties);
    }

    /**
     * True when all files are found or false if none are found
     * @param paths to check existence of
     * @return true if all files exist or false if none exist
     * @throws IllegalStateException when only a subset of files is found
     */
    private boolean filesExist(String ... paths) {
        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String path : paths) {
            if (new File(path).exists()) {
                found.add(path);
            } else {
                missing.add(path);
            }
        }

        if (!found.isEmpty() && !missing.isEmpty()) {
            String message = "Found " + String.join(", ", found) + " but was missing "
                    + String.join(", ", missing);
            throw new IllegalStateException(message);
        }

        return missing.isEmpty();
    }
}
