/*
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
package org.deltafi.common.ssl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SslContextFactoryTest {

    @Test
    void testSslContextFactory() throws SslContextFactory.SslException {
        Assertions.assertThat(SslContextFactory.buildSslContext(sslProperties())).isNotNull();
    }

    @Test
    void testSslContextFactory_missingKeystore() {
        SslProperties sslProperties = sslProperties();
        sslProperties.setKeyStore("missing.p12");
        Assertions.assertThatThrownBy(() -> SslContextFactory.buildSslContext(sslProperties))
                .isInstanceOf(SslContextFactory.SslException.class)
                .hasMessage("java.io.FileNotFoundException: missing.p12 (No such file or directory)");
    }

    @Test
    void testSslContextFactory_badPassword() {
        SslProperties sslProperties = sslProperties();
        sslProperties.setKeyStorePassword("wrong");
        Assertions.assertThatThrownBy(() -> SslContextFactory.buildSslContext(sslProperties))
                .isInstanceOf(SslContextFactory.SslException.class)
                .hasMessage("java.io.IOException: keystore password was incorrect");
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