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
import java.util.Map;

@Slf4j
@Service
public class EntityAuthorizationResolver {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AuthResponse GRANTED = new AuthResponse(true, null);
    private static final AuthResponse DENIED = new AuthResponse(false, null);

    private final HttpClient httpClient;
    private final String entityAuthorizationUrl;
    private final boolean enabled;

    public EntityAuthorizationResolver(HttpClient httpClient, @Value("${ENTITY_AUTHORIZER_URL:http://127.0.0.1:8080/}") String entityResolverUrl, @Value("${ENTITY_AUTHORIZER_ENABLED:false}") boolean enabled) {
        this.httpClient = httpClient;
        this.entityAuthorizationUrl = entityResolverUrl;
        this.enabled = enabled;
    }

    public AuthResponse isAuthorized(Map<String, String> metadata) {
        AuthResponse authorized = GRANTED;
        if (enabled) {
            String identifier = DeltaFiUserService.currentUsername();
            authorized = callEntityResolver(new AuthBody(identifier, metadata));
            log.debug("User \"{}\" was {} access{} using metadata {}", identifier, authorized.authorized ? "granted" : "denied", metadata,
                    authorized.reason != null ? " because " + authorized.reason : "");
        }

        return authorized;
    }

    private AuthResponse callEntityResolver(AuthBody body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
                    .uri(URI.create(entityAuthorizationUrl))
                    .setHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return mapResponse(response.body());
        } catch (IOException e) {
            log.error("Error getting identifiers from {}", entityAuthorizationUrl, e);
        } catch (InterruptedException e) {
            log.error("Interrupted while getting identifiers from {}", entityAuthorizationUrl, e);
            Thread.currentThread().interrupt();
        }

        // do not allow access when the call to the entity authentication endpoint fails for any reason
        return DENIED;
    }

    private AuthResponse mapResponse(String responseBody) {
        try {
            return OBJECT_MAPPER.readValue(responseBody, new TypeReference<>() {});
        } catch (IOException e) {
            log.error("Error parsing the response from {}, response of {}", entityAuthorizationUrl, responseBody, e);
            return DENIED;
        }
    }

    record AuthBody(String identifier, Map<String, String> metadata) {}
    public record AuthResponse(boolean authorized, String reason) {}
}
