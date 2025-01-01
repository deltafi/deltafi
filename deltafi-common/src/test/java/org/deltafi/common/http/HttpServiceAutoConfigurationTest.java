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
package org.deltafi.common.http;

import org.deltafi.common.ssl.SslContextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class HttpServiceAutoConfigurationTest {

    final HttpClientCustomizer customizer = builder -> builder.connectTimeout(Duration.ofSeconds(10L));
    final HttpServiceAutoConfiguration autoConfiguration = new HttpServiceAutoConfiguration(List.of(customizer));

    @Test
    void httpClient_sslConfigured() {
        SslContextProvider provider = Mockito.mock(SslContextProvider.class);
        Mockito.when(provider.isConfigured()).thenReturn(true);
        Mockito.when(provider.createSslContext()).thenReturn(Mockito.mock(SSLContext.class));
        autoConfiguration.httpClient(provider);
        Mockito.verify(provider).createSslContext();
    }

    @Test
    void httpClient_sslNotConfigured() {
        SslContextProvider provider = Mockito.mock(SslContextProvider.class);
        Mockito.when(provider.isConfigured()).thenReturn(false);
        autoConfiguration.httpClient(provider);
        Mockito.verify(provider, Mockito.never()).createSslContext();
    }

    @Test
    void testCustomizationApplied() {
        HttpClient client = autoConfiguration.httpClient(null);
        assertEquals(Duration.ofSeconds(10L), client.connectTimeout().orElse(null));
    }
}