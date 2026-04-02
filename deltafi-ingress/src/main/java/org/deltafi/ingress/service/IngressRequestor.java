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
package org.deltafi.ingress.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.uuid.UUIDGenerator;

import java.net.http.HttpClient;

@RequiredArgsConstructor
@Slf4j
public class IngressRequestor {

    private final DeltaFiIngressService ingressService;
    private final ContentStorageService contentStorageService;

    public IngressRequestor(ContentStorageService contentStorageService, String baseUrl, HttpClient httpClient,
            String appName, UUIDGenerator uuidGenerator) {
        this(new DeltaFiIngressService(baseUrl, httpClient, appName, uuidGenerator), contentStorageService);
    }

    /**
     * Return an Ingress Request builder that will pass the content as the HttpRequest body directly
     * to the DeltaFi ingress endpoint
     *
     * @return IngressRequest which can be configured and executed
     */
    public IngressRequest.ByValue byValue() {
        return new IngressRequest.ByValue(ingressService);
    }

    /**
     * Return an Ingress Request builder which allows storing the content to the local content storage system.
     * The content references will be attached to the request and make up the body of the HttpRequest
     * that is sent to the DeltaFi ingress by content references endpoint
     *
     * @return IngressRequest which can be configured, attach content and executed
     */
    public IngressRequest.ByRef byReference() {
        return new IngressRequest.ByRef(ingressService, contentStorageService);
    }
}
