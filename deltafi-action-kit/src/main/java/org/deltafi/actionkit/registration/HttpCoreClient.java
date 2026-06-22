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
// ABOUTME: HTTP implementation of CoreClient using the JDK HttpClient with exponential backoff.
// ABOUTME: Fails fast on 4xx, retries 5xx and network errors three times before giving up.
package org.deltafi.actionkit.registration;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.PluginRegistration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Posts plugin registrations over HTTP to a DeltaFi core's {@code /plugins} endpoint.
 */
@Slf4j
public class HttpCoreClient implements CoreClient {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build();

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 500L;
    private static final long MAX_BACKOFF_MS = 2_000L;
    private static final double BACKOFF_MULTIPLIER = 1.5;

    private final String coreUrl;
    private final HttpClient httpClient;

    public HttpCoreClient(String coreUrl) {
        this.coreUrl = coreUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void register(PluginRegistration registration) {
        HttpRequest request = buildHttpRequest(registration);

        long backoff = INITIAL_BACKOFF_MS;
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return;
                }
                if (status >= 400 && status < 500) {
                    throw new IllegalStateException("Plugin registration rejected by core (HTTP " + status + "): "
                            + response.body());
                }
                log.warn("Plugin registration attempt {}/{} returned HTTP {}: {}", attempt, MAX_ATTEMPTS, status,
                        response.body());
                lastFailure = new IOException("HTTP " + status);
            } catch (IOException e) {
                log.warn("Plugin registration attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                lastFailure = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Plugin registration interrupted", e);
            }

            backoff = backoffAndRecalc(attempt,  backoff);
        }
        throw new IllegalStateException("Plugin registration failed after " + MAX_ATTEMPTS + " attempts", lastFailure);
    }

    private HttpRequest buildHttpRequest(PluginRegistration registration) {
        try {
            byte[] body = OBJECT_MAPPER.writeValueAsBytes(registration);
            return HttpRequest.newBuilder()
                    .uri(URI.create(coreUrl + "/plugins"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize plugin registration", e);
        }
    }

    private long backoffAndRecalc(int attempt, long backoff) {
        if (attempt < MAX_ATTEMPTS) {
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Plugin registration interrupted", e);
            }
        }

        return Math.min((long) (backoff * BACKOFF_MULTIPLIER), MAX_BACKOFF_MS);
    }

    @Override
    public void close() {
        httpClient.close();
    }
}
