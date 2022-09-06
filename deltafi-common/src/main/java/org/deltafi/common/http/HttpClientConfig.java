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
package org.deltafi.common.http;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.ssl.SslContextFactory;
import org.deltafi.common.ssl.SslProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@Slf4j
public class HttpClientConfig {
    @Bean
    @ConfigurationProperties("ssl")
    public SslProperties sslProperties() {
        return new SslProperties();
    }

    @Bean
    public HttpClient httpClient(SslProperties sslProperties) {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();

        if (sslProperties.isEnabled()) {
            try {
                httpClientBuilder.sslContext(SslContextFactory.buildSslContext(sslProperties));
            } catch (SslContextFactory.SslException e) {
                log.warn("Unable to build SSL context. SSL will be disabled.", e);
            }
        }

        return httpClientBuilder.build();
    }
}
