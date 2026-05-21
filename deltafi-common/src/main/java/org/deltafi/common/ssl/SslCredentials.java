/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

/**
 * Holds a pre-built {@link SSLContext}, the key store it was built from, and the trust managers
 * for that bundle. Framework-agnostic — constructed by framework-specific factories (e.g. the
 * deprecated {@code SslContextProvider} subclass in {@code deltafi-common-spring} builds one from
 * Spring PEM properties).
 */
@Getter
public class SslCredentials {

    private final SSLContext sslContext;
    private final TrustManager[] trustManagers;
    private final KeyStore keyStore;
    private final String keyAlias;
    private final char[] keyPassword;
    private final boolean configured;

    /**
     * Construct from a pre-built SSLContext and key/trust material.
     *
     * @param sslContext  pre-built SSLContext, or null if SSL is not configured
     * @param trustManagers trust managers from the bundle, or null if SSL is not configured
     * @param keyStore    the key store the SSLContext was built from, or null if SSL is not configured
     * @param keyAlias    alias of the private key entry within {@code keyStore}, or null
     * @param keyPassword password for the private key entry, or null if no password
     */
    public SslCredentials(SSLContext sslContext, TrustManager[] trustManagers,
                          KeyStore keyStore, String keyAlias, char[] keyPassword) {
        this.sslContext = sslContext;
        this.trustManagers = trustManagers;
        this.keyStore = keyStore;
        this.keyAlias = keyAlias;
        this.keyPassword = keyPassword;
        this.configured = sslContext != null && keyStore != null;
    }

    /** Credentials representing an unconfigured TLS setup; {@link #createSslContext()} returns null. */
    public static SslCredentials unconfigured() {
        return new SslCredentials(null, null, null, null, null);
    }

    /** Return the SSLContext, or null if SSL is not configured. */
    public SSLContext createSslContext() {
        return configured ? sslContext : null;
    }

    /**
     * Get the private key from the KeyStore.
     *
     * @return private key or null if SSL is not configured
     * @throws SslException if the key cannot be extracted from the keystore
     */
    public Key getPrivateKey() throws SslException {
        if (!configured) {
            return null;
        }

        try {
            return keyStore.getKey(keyAlias, keyPassword);
        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new SslException("Failed to extract the key from the keystore", e);
        }
    }

    /** Exception thrown when SSL key or context operations fail. */
    public static class SslException extends Exception {
        public SslException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
