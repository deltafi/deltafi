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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.IngressEventItem;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.ingress.exception.IngressException;
import org.deltafi.ingress.service.IngressRequest.ByRef;
import org.deltafi.ingress.service.IngressRequest.ByValue;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;

@Slf4j
public class DeltaFiIngressService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final HttpClient httpClient;
    private final String appName;
    private final String baseUrl;
    private final UUIDGenerator uuidGenerator;

    public DeltaFiIngressService(String baseUrl, HttpClient httpClient, String appName, UUIDGenerator uuidGenerator) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.httpClient = httpClient;
        this.appName = appName;
        this.uuidGenerator = uuidGenerator;
    }

    public void ingress(IngressRequest<?> ingressRequest) throws IngressException {
        try {
            HttpRequest request = buildRequest(ingressRequest);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Failed to ingress to DeltaFi - {} {}  ", response.statusCode(), response.body());
                String message = "Failed to ingress the content with status code: " + response.statusCode() +
                        " with body " + response.body();
                throw new IngressException(message);
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IngressException("Interrupted while executing ingress", interruptedException);
        } catch (IOException e) {
            throw new IngressException("IOException/Failed to ingress content to DeltaFi " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new IngressException("IllegalArgumentException/Failed to ingress content to DeltaFi " + e.getMessage(), e);
        } catch (SecurityException e) {
            throw new IngressException("SecurityException/Failed to ingress content to DeltaFi " + e.getMessage(), e);
        }
    }

    HttpRequest buildRequest(IngressRequest<?> ingressRequest) throws JsonProcessingException {
        HttpRequest.Builder requestBuilder = switch (ingressRequest) {
            case IngressRequest.ByValue ingressByValue -> requestBuilder(ingressByValue);
            case IngressRequest.ByRef ingressByRef -> requestBuilder(ingressByRef);
            default -> throw new IllegalArgumentException("Unknown content value " + ingressRequest.getClass());
        };

        requestBuilder.header("X-User-Name", appName);
        requestBuilder.header("X-User-Permissions", "DeltaFileIngress");
        return requestBuilder.build();
    }

    HttpRequest.Builder requestBuilder(ByValue ingressByValue) throws JsonProcessingException {
        String fullurl = baseUrl + "api/v2/deltafile/ingress";
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(fullurl))
                .POST(HttpRequest.BodyPublishers.ofByteArray(ingressByValue.bytes()));

        String contentType = ingressByValue.mediaType() != null ? ingressByValue.mediaType() : "application/octet-stream";
        String filename = ingressByValue.deltaFileName() != null ? ingressByValue.deltaFileName() : uuidGenerator.generate().toString();

        if (ingressByValue.metadata() != null) {
            // map it the metadata header
            String metaAsJson = OBJECT_MAPPER.writeValueAsString(ingressByValue.metadata());
            requestBuilder.header("Metadata", metaAsJson);
        }

        requestBuilder.header("DataSource", ingressByValue.dataSource());
        requestBuilder.header("Content-Type", contentType);
        requestBuilder.header("Filename", filename);
        return requestBuilder;
    }

    HttpRequest.Builder requestBuilder(ByRef ingressByRef) throws JsonProcessingException {
        IngressEventItem ingressEventItem = IngressEventItem.builder()
                .did(ingressByRef.did())
                .flowName(ingressByRef.dataSource())
                .deltaFileName(ingressByRef.deltaFileName())
                .content(ingressByRef.content())
                .metadata(ingressByRef.metadata())
                .annotations(ingressByRef.annotations())
                .build();
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "api/v2/ingress/event"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(ingressEventItem)));
    }
}
