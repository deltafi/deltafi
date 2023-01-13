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
package org.deltafi.common.http;

import org.deltafi.common.ssl.SslContextFactory;
import org.deltafi.common.ssl.SslProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class HttpServiceAutoConfigurationTest {

    HttpServiceAutoConfiguration autoConfiguration = new HttpServiceAutoConfiguration();

    @Test
    void httpClient_sslConfigured() {
        try (MockedStatic<SslContextFactory> mockSslContextFactory = Mockito.mockStatic(SslContextFactory.class)) {
            SslProperties sslProperties = sslProperties();

            mockSslContextFactory.when(() -> SslContextFactory.buildSslContext(sslProperties)).thenCallRealMethod();

            autoConfiguration.httpClient(sslProperties);

            mockSslContextFactory.verify(() -> SslContextFactory.buildSslContext(sslProperties));
        }
    }

    @Test
    void httpClient_sslNotConfigured() {
        try (MockedStatic<SslContextFactory> mockSslContextFactory = Mockito.mockStatic(SslContextFactory.class)) {
            SslProperties sslProperties = sslProperties();
            sslProperties.setKeyStorePassword(SslProperties.NOT_SET);

            autoConfiguration.httpClient(sslProperties);

            mockSslContextFactory.verifyNoInteractions();
        }
    }

    @Test
    void testTryAlternativeEnvVariables() {
        SslProperties sslProperties = new SslProperties();
        sslProperties.setKeyStorePassword(null);
        sslProperties.setTrustStorePassword(null);

        autoConfiguration.tryAlternativeEnvVariables(sslProperties);

        // null passwords are replaced with not-set by readEnvVar
        assertEquals(SslProperties.NOT_SET, sslProperties.getKeyStorePassword());
        assertEquals(SslProperties.NOT_SET, sslProperties.getTrustStorePassword());
    }

    SslProperties sslProperties() {
        SslProperties sslProperties = new SslProperties();
        sslProperties.setKeyStore("src/test/resources/mockKeystore.p12");
        sslProperties.setKeyStorePassword("password");
        sslProperties.setKeyStoreType("PKCS12");
        sslProperties.setTrustStore("src/test/resources/mockTrustStore.jks");
        sslProperties.setTrustStorePassword("storePassword");
        sslProperties.setTrustStoreType("JKS");
        sslProperties.setProtocol("TLSv1.2");

        return sslProperties;
    }
}