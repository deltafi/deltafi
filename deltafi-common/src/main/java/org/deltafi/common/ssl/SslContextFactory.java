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
package org.deltafi.common.ssl;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;

public class SslContextFactory {
    public static class SslException extends Exception {
        public SslException(Throwable cause) {
            super(cause);
        }
    }

    public static SSLContext buildSslContext(SslProperties sslProperties) throws SslException {
        try {
            SSLContext context = SSLContext.getInstance(sslProperties.getProtocol());
            context.init(keyManagers(sslProperties), trustManagers(sslProperties), SecureRandom.getInstanceStrong());
            return context;
        } catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException |
                UnrecoverableKeyException | KeyManagementException e) {
            throw new SslException(e);
        }
    }

    private static KeyManager[] keyManagers(SslProperties sslProperties) throws IOException, KeyStoreException,
            CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore keyStore = loadKeyStore(sslProperties.getKeyStore(), sslProperties.getKeyStoreType(),
                sslProperties.getKeyStorePassword());
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, sslProperties.getKeyStorePassword().toCharArray());
        return keyManagerFactory.getKeyManagers();
    }

    private static TrustManager[] trustManagers(SslProperties sslProperties)
            throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        KeyStore trustStore = loadKeyStore(sslProperties.getTrustStore(), sslProperties.getTrustStoreType(),
                sslProperties.getTrustStorePassword());
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory.getTrustManagers();
    }

    private static KeyStore loadKeyStore(String keyStoreFile, String keyStoreType, String keyStorePassword)
            throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        try (FileInputStream keyStoreInputStream = new FileInputStream(keyStoreFile)) {
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(keyStoreInputStream, keyStorePassword.toCharArray());
            return keyStore;
        }
    }
}
