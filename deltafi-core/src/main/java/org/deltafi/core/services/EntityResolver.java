/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class EntityResolver {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String entityResolverUrl;
    private final boolean enabled;

    public EntityResolver(HttpClient httpClient, @Value("${ENTITY_RESOLVER_URL:http://127.0.0.1:8080/}") String entityResolverUrl, @Value("${ENTITY_RESOLVER_ENABLED:false}") boolean enabled) {
        this.httpClient = httpClient;
        this.entityResolverUrl = entityResolverUrl;
        this.enabled = enabled;
    }

    public Set<String> resolve(String identifier) {
        Set<String> entities = new HashSet<>();
        entities.add(identifier);
        if (enabled) {
            List<String> resolvedEntities = callEntityResolver(identifier);
            log.debug("Resolved \"{}\" to {}", identifier, resolvedEntities);
            entities.addAll(resolvedEntities);
        }

        return entities;
    }

    private List<String> callEntityResolver(String identifier) {
        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString("[\"" + identifier + "\"]"))
                .uri(URI.create(entityResolverUrl))
                .setHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return mapResponse(response.body());
        } catch (IOException e) {
            log.error("Error getting identifiers from {}", entityResolverUrl, e);
        } catch (InterruptedException e) {
            log.error("Interrupted while getting identifiers from {}", entityResolverUrl, e);
            Thread.currentThread().interrupt();
        }
        return List.of();
    }

    private List<String> mapResponse(String responseBody) {
        try {
            return OBJECT_MAPPER.readValue(responseBody, new TypeReference<>() {});
        } catch (IOException e) {
            log.error("Error parsing the response from {}, response of {}", entityResolverUrl, responseBody, e);
            return List.of();
        }
    }
}
