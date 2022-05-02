/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.config;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.exception.SslConfigException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Optional;

@Slf4j
public class HttpClientConfig {

    @Produces
    @ApplicationScoped
    public HttpClient httpClient(ActionKitConfig config) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder();
        config.ssl().flatMap(this::getSslContext).ifPresent(clientBuilder::sslContext);
        return clientBuilder.build();
    }

    private Optional<SSLContext> getSslContext(SslConfig config) {
        return keystoreExists(config.keyStore()) ? Optional.of(toSslContext(config)) : Optional.empty();
    }

    private SSLContext toSslContext(SslConfig sslConfig) {
        try {
            log.info("Setting up secure client from key: {} and trust: {}", sslConfig.keyStore(), sslConfig.trustStore());
            SSLContext context = SSLContext.getInstance(sslConfig.protocol());
            context.init(keyManagers(sslConfig), trustManagers(sslConfig), SecureRandom.getInstanceStrong());
            return context;
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw new SslConfigException(e);
        }
    }

    private TrustManager[] trustManagers(SslConfig sslConfig) {
        try {
            KeyStore trustStore = loadKeyStore(sslConfig.trustStore(), sslConfig.trustStoreType(), sslConfig.trustStorePassword());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            return tmf.getTrustManagers();
        } catch (KeyStoreException | NoSuchAlgorithmException e) {
            throw new SslConfigException(e);
        }
    }

    private KeyManager[] keyManagers(SslConfig sslConfig) {
        try {
            KeyStore ks = loadKeyStore(sslConfig.keyStore(), sslConfig.keyStoreType(), sslConfig.keyStorePassword());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, sslConfig.keyStorePassword().toCharArray());
            return kmf.getKeyManagers();
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new SslConfigException(e);
        }
    }

    private KeyStore loadKeyStore(String location, String type, String password) {
        try (FileInputStream keys = new FileInputStream(location)) {
            KeyStore keystore = KeyStore.getInstance(type);
            keystore.load(keys, password.toCharArray());
            return keystore;
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException exception) {
            log.error("Failed to load {} with type {}", location, type, exception);
            throw new SslConfigException(exception);
        }
    }

    private boolean keystoreExists(String path) {
        File keystorePath = new File(path);
        log.info("Checking for keyStore at {}, SSL will be enabled: {}", path, keystorePath.exists());
        return keystorePath.exists();
    }
}
