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
package org.deltafi.actionkit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.exception.HttpPostException;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * General purpose HTTP client service
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HttpService {
    private final HttpClient httpClient;

    /**
     * Post data to an HTTP endpoint
     * @param url URL endpoint where data will be posted
     * @param headers Map of key-value pairs for HTTP header fields and values
     * @param body Body content to be posted to the HTTP endpoint
     * @param mediaType Media type of the HTTP post
     * @return an HTTP response object with success/failure details
     * @throws HttpPostException when the HTTP client throws an IOException, InterruptedException, IllegalArgumentException , or SecurityException
     */
    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    public java.net.http.HttpResponse<InputStream> post(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpPostException {
        Supplier<InputStream> is = () -> body;
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .setHeader("content-type", mediaType)
                .POST(HttpRequest.BodyPublishers.ofInputStream(is));
        addHeaders(requestBuilder, headers);

        HttpRequest request = requestBuilder.build();

        try {
            // TODO: Should exceptions be thrown for 4xx return codes?
            return httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException | IllegalArgumentException | SecurityException e) {
            throw new HttpPostException(e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Add a string map of headers to an HttpRequest
     * @param builder Builder used to create an HttpRequest
     * @param headers String map of headers to add to the HttpRequest
     */
    static private void addHeaders(@NotNull HttpRequest.Builder builder, @NotNull Map<String, String> headers) {
        if (!headers.isEmpty()) {
            builder.headers(
                    headers.entrySet().stream().flatMap(x -> Stream.of(x.getKey(), x.getValue())).toArray(String[]::new)
            );
        }
    }
}
