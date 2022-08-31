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
package org.deltafi.common.config;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.ssl.SslContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;

@Slf4j
@Configuration
@Component
public class HttpClientConfig {
    @Bean
    public HttpClient httpClient(ActionsProperties config) {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
        if (config.getSsl() != null) {
            try {
                httpClientBuilder.sslContext(SslContextFactory.buildSslContext(config.getSsl()));
            } catch (SslContextFactory.SslException e) {
                log.warn("Unable to build SSL context. SSL will be disabled.", e);
            }
        }
        return httpClientBuilder.build();
    }
}
