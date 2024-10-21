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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ssl.PemSslBundleProperties;

import java.io.UncheckedIOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SslContextProviderTest {

    @Test
    void createSslContext() {
        PemSslBundleProperties properties = new PemSslBundleProperties();
        properties.setProtocol("TLSv1.2");
        properties.getKeystore().setPrivateKey("classpath:privatekey.pem");
        properties.getKeystore().setCertificate("classpath:cert.pem");
        properties.getTruststore().setCertificate("classpath:ca_chain.pem");
        SslContextProvider provider = new SslContextProvider(properties);
        assertThat(provider.createSslContext()).isNotNull();
        assertThat(provider.isConfigured()).isTrue();
    }

    @Test
    void createSslContextWithPassword() throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
        PemSslBundleProperties properties = new PemSslBundleProperties();
        properties.setProtocol("TLSv1.2");
        properties.getKey().setAlias("my-alias");
        properties.getKeystore().setPrivateKey("classpath:key_with_pass.pem");
        properties.getKeystore().setPrivateKeyPassword("password");
        properties.getKeystore().setCertificate("classpath:cert.pem");
        properties.getTruststore().setCertificate("classpath:ca_chain.pem");
        SslContextProvider provider = new SslContextProvider(properties);
        assertThat(provider.createSslContext()).isNotNull();
        assertThat(provider.isConfigured()).isTrue();
        KeyStore keyStore = provider.getBundle().getStores().getKeyStore();
        assertThat(keyStore.getKey("my-alias", null)).isNotNull();
    }

    @Test
    void nullProperties() {
        SslContextProvider provider = new SslContextProvider(null);
        assertThat(provider.createSslContext()).isNull();
        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    void emptyProperties() {
        SslContextProvider provider = new SslContextProvider(new PemSslBundleProperties());
        assertThat(provider.createSslContext()).isNull();
        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    void missingKeyFile() {
        PemSslBundleProperties properties = new PemSslBundleProperties();
        properties.setProtocol("TLSv1.2");
        properties.getKeystore().setPrivateKey("missingkey.pem");
        properties.getKeystore().setCertificate("classpath:cert.pem");
        properties.getTruststore().setCertificate("classpath:ca_chain.pem");
        assertThatThrownBy(() -> new SslContextProvider(properties))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("Error reading certificate or key from file 'missingkey.pem'");
    }

    @Test
    void invalidPassword() {
        PemSslBundleProperties properties = new PemSslBundleProperties();
        properties.setProtocol("TLSv1.2");
        properties.getKeystore().setPrivateKey("classpath:key_with_pass.pem");
        properties.getKeystore().setPrivateKeyPassword("wrongPassword");
        properties.getKeystore().setCertificate("classpath:cert.pem");
        properties.getTruststore().setCertificate("classpath:ca_chain.pem");
        assertThatThrownBy(() -> new SslContextProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Error loading private key file: Error decrypting private key");
    }

    @Test
    void missingPassword() {
        PemSslBundleProperties properties = new PemSslBundleProperties();
        properties.setProtocol("TLSv1.2");
        properties.getKeystore().setPrivateKey("classpath:key_with_pass.pem");
        properties.getKeystore().setCertificate("classpath:cert.pem");
        properties.getTruststore().setCertificate("classpath:ca_chain.pem");
        assertThatThrownBy(() -> new SslContextProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Error loading private key file: Password is required for an encrypted private key");
    }
}