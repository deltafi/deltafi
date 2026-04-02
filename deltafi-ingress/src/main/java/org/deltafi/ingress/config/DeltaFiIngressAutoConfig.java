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
package org.deltafi.ingress.config;

import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.ingress.service.IngressRequestor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;

@AutoConfiguration
@EnableConfigurationProperties(DeltaFiProperties.class)
public class DeltaFiIngressAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public IngressRequestor ingressRequestor(DeltaFiProperties deltaFiProperties,
            ContentStorageService contentStorageService, HttpClient httpClient, BuildProperties buildProperties,
            UUIDGenerator uuidGenerator) {
        String baseUrl = deltaFiProperties.getBaseCoreUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://deltafi-core:8080";
        }
        return new IngressRequestor(contentStorageService, baseUrl, httpClient, buildProperties.getName(),
                uuidGenerator);
    }
}
