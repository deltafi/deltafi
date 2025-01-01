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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.ssl.SslAutoConfiguration;
import org.deltafi.common.ssl.SslContextProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Slf4j
@AutoConfiguration
@AutoConfigureAfter(SslAutoConfiguration.class)
public class HttpServiceAutoConfiguration {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofMillis(1000L);
    private final List<HttpClientCustomizer> httpClientCustomizers;

    public HttpServiceAutoConfiguration(List<HttpClientCustomizer> httpClientCustomizers) {
        this.httpClientCustomizers = httpClientCustomizers;
    }

    @Bean
    public HttpClient httpClient(SslContextProvider sslContextProvider) {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
        httpClientBuilder.connectTimeout(DEFAULT_CONNECT_TIMEOUT);

        if (sslContextProvider != null && sslContextProvider.isConfigured()) {
            httpClientBuilder.sslContext(sslContextProvider.createSslContext());
        }

        if (httpClientCustomizers != null) {
            httpClientCustomizers.forEach(httpClientCustomizer -> httpClientCustomizer.customize(httpClientBuilder));
        }

        return httpClientBuilder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpService httpService(HttpClient httpClient) {
        return new HttpService(httpClient);
    }

}
