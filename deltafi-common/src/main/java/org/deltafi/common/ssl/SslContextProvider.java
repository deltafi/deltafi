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

import lombok.Getter;
import org.springframework.boot.autoconfigure.ssl.PemSslBundleProperties;
import org.springframework.boot.autoconfigure.ssl.PropertiesSslBundle;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;

import javax.net.ssl.SSLContext;
import java.security.*;
import java.util.Objects;

@Getter
public class SslContextProvider {
    private final SslBundle bundle;
    private final boolean configured;

    public SslContextProvider(PemSslBundleProperties properties) {
        this.bundle = PropertiesSslBundle.get(Objects.requireNonNullElseGet(properties, PemSslBundleProperties::new));
        this.configured = bundle.getStores().getKeyStore() != null;
    }

    /**
     * Return a new SSLContext build from the SslBundle if it is configured
     * @return a new SSLContext or null if it is not setup
     */
    public SSLContext createSslContext() {
        return configured ? bundle.createSslContext() : null;
    }

    /**
     * Get the private key from the KeyStore
     * @return private key or null if SSL is not configured
     */
    public Key getPrivateKey() throws SslException {
        if (!configured) {
            return null;
        }

        KeyStore keyStore = bundle.getStores().getKeyStore();
        SslBundleKey key = bundle.getKey();

        try {
            char[] keyPass = key.getPassword() != null ? key.getPassword().toCharArray() : null;
            return keyStore.getKey(key.getAlias(), keyPass);
        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new SslException("Failed to extract the key from the keystore", e);
        }
    }

    public static class SslException extends Exception {
        public SslException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
