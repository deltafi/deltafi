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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.Supplier;

/**
 * General purpose HTTP client service
 */
@RequiredArgsConstructor
@Slf4j
public class HttpService {
    private static final String CONTENT_TYPE = "content-type";

    private final HttpClient httpClient;

    public static HttpRequest.Builder newRequestBuilder(@NotNull String url, @NotNull Map<String, String> headers, @NotNull String mediaType) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        headers.forEach(requestBuilder::header);
        if (!headers.containsKey(CONTENT_TYPE)) {
            requestBuilder.header(CONTENT_TYPE, mediaType);
        }
        return requestBuilder;
    }

    public HttpResponse<InputStream> execute(@NotNull HttpRequest request) throws HttpSendException {
        try {
            // TODO: Should exceptions be thrown for 4xx return codes?
            return httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException | IllegalArgumentException | SecurityException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new HttpSendException(e.getClass().getSimpleName(), request.method(), e.getMessage(), e);
        }
    }

    /**
     * Post data to an HTTP endpoint
     *
     * @param url       URL endpoint where data will be posted
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be posted to the HTTP endpoint
     * @param mediaType Media type of the HTTP post
     * @return an HTTP response object with success/failure details
     * @throws HttpPostException when the HTTP client throws an IOException, InterruptedException, IllegalArgumentException , or SecurityException
     */
    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    public HttpResponse<InputStream> post(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpPostException {
        Supplier<InputStream> is = () -> body;
        HttpRequest.Builder requestBuilder = newRequestBuilder(url, headers, mediaType);
        requestBuilder.POST(HttpRequest.BodyPublishers.ofInputStream(is));
        HttpRequest request = requestBuilder.build();
        return execute(request);
    }

    /**
     * Put data to an HTTP endpoint
     *
     * @param url       URL endpoint where data will be put
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be put to the HTTP endpoint
     * @param mediaType Media type of the HTTP put
     * @return an HTTP response object with success/failure details
     * @throws HttpSendException when the HTTP client throws an IOException, InterruptedException, IllegalArgumentException , or SecurityException
     */
    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    public HttpResponse<InputStream> put(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        Supplier<InputStream> is = () -> body;
        HttpRequest.Builder requestBuilder = newRequestBuilder(url, headers, mediaType);
        requestBuilder.PUT(HttpRequest.BodyPublishers.ofInputStream(is));
        HttpRequest request = requestBuilder.build();
        return execute(request);
    }

    /**
     * Patch data at an HTTP endpoint
     *
     * @param url       URL endpoint where data will be sent as a patch
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be patched to the HTTP endpoint
     * @param mediaType Media type of the HTTP patch
     * @return an HTTP response object with success/failure details
     * @throws HttpSendException when the HTTP client throws an IOException, InterruptedException, IllegalArgumentException , or SecurityException
     */
    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    public HttpResponse<InputStream> patch(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        Supplier<InputStream> is = () -> body;
        HttpRequest.Builder requestBuilder = newRequestBuilder(url, headers, mediaType);
        requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofInputStream(is));
        HttpRequest request = requestBuilder.build();
        return execute(request);
    }

    /**
     * Delete data from an HTTP endpoint
     *
     * @param url       URL endpoint where data will be deleted from
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param mediaType Media type of the HTTP delete
     * @return an HTTP response object with success/failure details
     * @throws HttpSendException when the HTTP client throws an IOException, InterruptedException, IllegalArgumentException , or SecurityException
     */
    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    public HttpResponse<InputStream> delete(@NotNull String url, @NotNull Map<String, String> headers, @NotNull String mediaType) throws HttpSendException {
        HttpRequest.Builder requestBuilder = newRequestBuilder(url, headers, mediaType);
        requestBuilder.DELETE();
        HttpRequest request = requestBuilder.build();
        return execute(request);
    }
}
