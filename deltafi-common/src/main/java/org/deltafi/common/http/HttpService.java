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
package org.deltafi.common.http;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * General purpose HTTP client service.
 *
 * <p>Four families of convenience methods are provided for each HTTP verb. All but the first fully consume
 * the response body and return the connection to the pool for reuse, so callers need not touch the body:</p>
 * <ul>
 *     <li>{@code post}/{@code put}/{@code patch}/{@code delete} (<strong>deprecated</strong>) return
 *     {@link HttpResponse}{@code <}{@link InputStream}{@code >}, a live connection-backed stream whose
 *     lifecycle the caller owns. The outcome depends entirely on what the caller does with the body:
 *     <ul>
 *         <li>drain it (read to EOF) then close it &rarr; the connection is returned to the pool and reused;</li>
 *         <li>close it without draining &rarr; the connection is torn down — no leak, but it cannot be reused
 *         (connection churn);</li>
 *         <li>do neither &rarr; the connection stays checked out and sockets accumulate — a leak.</li>
 *     </ul>
 *     Prefer one of the variants below, which drain and release the connection for you.</li>
 *     <li>{@code *ForBytes} return {@link HttpResponse}{@code <}{@code byte[]}{@code >}. The body is fully
 *     read into memory before the method returns and the connection is returned to the pool for reuse, so
 *     there is nothing to close and the return value may be safely ignored. Prefer these when the response
 *     is small.</li>
 *     <li>{@code *DiscardingBody} return {@link HttpResponse}{@code <}{@link Void}{@code >}. The body is
 *     drained and discarded (without buffering) and the connection is returned to the pool for reuse. Prefer
 *     these for fire-and-forget calls where the response body is not needed.</li>
 *     <li>{@code *ForStream} return a closeable {@link HttpInputStreamResponse}. The body is streamed rather
 *     than buffered; the caller <strong>must close</strong> the returned object (via try-with-resources) to
 *     release the connection. Reading the body to EOF before closing returns the connection to the pool for
 *     reuse; closing early abandons it (torn down, not reused). Prefer these when the response body is large
 *     and must be consumed as a stream.</li>
 * </ul>
 */
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class HttpService {
    private static final String CONTENT_TYPE = "content-type";
    public static final String PATCH_METHOD = "PATCH";
    public static final String DELETE_METHOD = "DELETE";

    private final HttpClient httpClient;

    /**
     * Create an {@link HttpRequest.Builder} with the given URL, headers, and content type.
     * @param url the request URL
     * @param headers headers to add to the request
     * @param mediaType the content type to set if not already present in headers
     * @return a configured request builder
     */
    public static HttpRequest.Builder newRequestBuilder(@NotNull String url, @NotNull Map<String, String> headers, @NotNull String mediaType) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        headers.forEach(requestBuilder::header);
        if (!headers.containsKey(CONTENT_TYPE)) {
            requestBuilder.header(CONTENT_TYPE, mediaType);
        }
        return requestBuilder;
    }

    /** Build a request for the given method, streaming {@code body} as the request payload (ignored for DELETE). */
    private static HttpRequest buildRequest(@NotNull String method, @NotNull String url, @NotNull Map<String, String> headers,
            InputStream body, @NotNull String mediaType) {
        HttpRequest.Builder requestBuilder = newRequestBuilder(url, headers, mediaType);
        HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofInputStream(() -> body);
        switch (method) {
            case "POST" -> requestBuilder.POST(publisher);
            case "PUT" -> requestBuilder.PUT(publisher);
            case PATCH_METHOD -> requestBuilder.method(PATCH_METHOD, publisher);
            case DELETE_METHOD -> requestBuilder.DELETE();
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        return requestBuilder.build();
    }

    /**
     * Execute an HTTP request and return the response.
     * @param request the HTTP request to send
     * @param bodyHandler the response body handler
     * @param <T> the response body type
     * @return the HTTP response
     * @throws HttpSendException if sending the request fails
     */
    public <T> HttpResponse<T> execute(@NotNull HttpRequest request, @NotNull HttpResponse.BodyHandler<T> bodyHandler) throws HttpSendException {
        try {
            // TODO: Should exceptions be thrown for 4xx return codes?
            return httpClient.send(request, bodyHandler);
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
     * @deprecated the caller owns the returned {@link InputStream} body: drain it (read to EOF) then close it to
     * return the connection to the pool; closing without draining tears the connection down (no leak, but no reuse),
     * and doing neither leaks it. Prefer {@link #postForBytes} (buffered), {@link #postDiscardingBody} (drained and
     * discarded), or {@link #postForStream} (closeable streaming) — all of which drain and release the connection for reuse.
     */
    @Deprecated(forRemoval = true)
    @NotNull
    public HttpResponse<InputStream> post(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpPostException {
        return execute(buildRequest("POST", url, headers, body, mediaType), HttpResponse.BodyHandlers.ofInputStream());
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
     * @deprecated the caller owns the returned {@link InputStream} body: drain it (read to EOF) then close it to
     * return the connection to the pool; closing without draining tears the connection down (no leak, but no reuse),
     * and doing neither leaks it. Prefer {@link #putForBytes} (buffered), {@link #putDiscardingBody} (drained and
     * discarded), or {@link #putForStream} (closeable streaming) — all of which drain and release the connection for reuse.
     */
    @Deprecated(forRemoval = true)
    @NotNull
    public HttpResponse<InputStream> put(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        return execute(buildRequest("PUT", url, headers, body, mediaType), HttpResponse.BodyHandlers.ofInputStream());
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
     * @deprecated the caller owns the returned {@link InputStream} body: drain it (read to EOF) then close it to
     * return the connection to the pool; closing without draining tears the connection down (no leak, but no reuse),
     * and doing neither leaks it. Prefer {@link #patchForBytes} (buffered), {@link #patchDiscardingBody} (drained and
     * discarded), or {@link #patchForStream} (closeable streaming) — all of which drain and release the connection for reuse.
     */
    @Deprecated(forRemoval = true)
    @NotNull
    public HttpResponse<InputStream> patch(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        return execute(buildRequest(PATCH_METHOD, url, headers, body, mediaType), HttpResponse.BodyHandlers.ofInputStream());
    }

    /**
     * Delete data from an HTTP endpoint
     *
     * @param url       URL endpoint where data will be deleted from
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param mediaType Media type of the HTTP delete
     * @return an HTTP response object with success/failure details
     * @throws HttpSendException when the HTTP client throws an IOException, InterruptedException, IllegalArgumentException , or SecurityException
     * @deprecated the caller owns the returned {@link InputStream} body: drain it (read to EOF) then close it to
     * return the connection to the pool; closing without draining tears the connection down (no leak, but no reuse),
     * and doing neither leaks it. Prefer {@link #deleteForBytes} (buffered), {@link #deleteDiscardingBody} (drained and
     * discarded), or {@link #deleteForStream} (closeable streaming) — all of which drain and release the connection for reuse.
     */
    @Deprecated(forRemoval = true)
    @NotNull
    public HttpResponse<InputStream> delete(@NotNull String url, @NotNull Map<String, String> headers, @NotNull String mediaType) throws HttpSendException {
        return execute(buildRequest(DELETE_METHOD, url, headers, null, mediaType), HttpResponse.BodyHandlers.ofInputStream());
    }

    /**
     * Post data to an HTTP endpoint, reading the full response body into a byte array.
     * <p>The connection is released before this method returns; the return value may be safely ignored.</p>
     *
     * @param url       URL endpoint where data will be posted
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be posted to the HTTP endpoint
     * @param mediaType Media type of the HTTP post
     * @return an HTTP response whose body is the fully-read response bytes
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpResponse<byte[]> postForBytes(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        return execute(buildRequest("POST", url, headers, body, mediaType), HttpResponse.BodyHandlers.ofByteArray());
    }

    /**
     * Put data to an HTTP endpoint, reading the full response body into a byte array.
     * <p>The connection is released before this method returns; the return value may be safely ignored.</p>
     *
     * @param url       URL endpoint where data will be put
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be put to the HTTP endpoint
     * @param mediaType Media type of the HTTP put
     * @return an HTTP response whose body is the fully-read response bytes
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpResponse<byte[]> putForBytes(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        return execute(buildRequest("PUT", url, headers, body, mediaType), HttpResponse.BodyHandlers.ofByteArray());
    }

    /**
     * Patch data at an HTTP endpoint, reading the full response body into a byte array.
     * <p>The connection is released before this method returns; the return value may be safely ignored.</p>
     *
     * @param url       URL endpoint where data will be sent as a patch
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be patched to the HTTP endpoint
     * @param mediaType Media type of the HTTP patch
     * @return an HTTP response whose body is the fully-read response bytes
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpResponse<byte[]> patchForBytes(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        return execute(buildRequest(PATCH_METHOD, url, headers, body, mediaType), HttpResponse.BodyHandlers.ofByteArray());
    }

    /**
     * Delete data from an HTTP endpoint, reading the full response body into a byte array.
     * <p>The connection is released before this method returns; the return value may be safely ignored.</p>
     *
     * @param url       URL endpoint where data will be deleted from
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param mediaType Media type of the HTTP delete
     * @return an HTTP response whose body is the fully-read response bytes
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpResponse<byte[]> deleteForBytes(@NotNull String url, @NotNull Map<String, String> headers, @NotNull String mediaType) throws HttpSendException {
        return execute(buildRequest(DELETE_METHOD, url, headers, null, mediaType), HttpResponse.BodyHandlers.ofByteArray());
    }

    /**
     * Post data to an HTTP endpoint, draining and discarding the response body.
     * <p>The connection is released before this method returns; the return value may be safely ignored.</p>
     *
     * @param url       URL endpoint where data will be posted
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be posted to the HTTP endpoint
     * @param mediaType Media type of the HTTP post
     * @return an HTTP response with no body
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpResponse<Void> postDiscardingBody(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        return execute(buildRequest("POST", url, headers, body, mediaType), HttpResponse.BodyHandlers.discarding());
    }

    /**
     * Put data to an HTTP endpoint, draining and discarding the response body.
     * <p>The connection is released before this method returns; the return value may be safely ignored.</p>
     *
     * @param url       URL endpoint where data will be put
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be put to the HTTP endpoint
     * @param mediaType Media type of the HTTP put
     * @return an HTTP response with no body
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpResponse<Void> putDiscardingBody(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        return execute(buildRequest("PUT", url, headers, body, mediaType), HttpResponse.BodyHandlers.discarding());
    }

    /**
     * Patch data at an HTTP endpoint, draining and discarding the response body.
     * <p>The connection is released before this method returns; the return value may be safely ignored.</p>
     *
     * @param url       URL endpoint where data will be sent as a patch
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be patched to the HTTP endpoint
     * @param mediaType Media type of the HTTP patch
     * @return an HTTP response with no body
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpResponse<Void> patchDiscardingBody(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        return execute(buildRequest(PATCH_METHOD, url, headers, body, mediaType), HttpResponse.BodyHandlers.discarding());
    }

    /**
     * Delete data from an HTTP endpoint, draining and discarding the response body.
     * <p>The connection is released before this method returns; the return value may be safely ignored.</p>
     *
     * @param url       URL endpoint where data will be deleted from
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param mediaType Media type of the HTTP delete
     * @return an HTTP response with no body
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpResponse<Void> deleteDiscardingBody(@NotNull String url, @NotNull Map<String, String> headers, @NotNull String mediaType) throws HttpSendException {
        return execute(buildRequest(DELETE_METHOD, url, headers, null, mediaType), HttpResponse.BodyHandlers.discarding());
    }

    /**
     * Post data to an HTTP endpoint, returning the response as a closeable stream.
     * <p>The returned {@link HttpInputStreamResponse} owns a live, connection-backed body stream and
     * <strong>must be closed</strong> (via try-with-resources) to release the connection.</p>
     *
     * @param url       URL endpoint where data will be posted
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be posted to the HTTP endpoint
     * @param mediaType Media type of the HTTP post
     * @return a closeable response wrapping the streaming body
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpInputStreamResponse postForStream(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        return new HttpInputStreamResponse(execute(buildRequest("POST", url, headers, body, mediaType), HttpResponse.BodyHandlers.ofInputStream()));
    }

    /**
     * Put data to an HTTP endpoint, returning the response as a closeable stream.
     * <p>The returned {@link HttpInputStreamResponse} owns a live, connection-backed body stream and
     * <strong>must be closed</strong> (via try-with-resources) to release the connection.</p>
     *
     * @param url       URL endpoint where data will be put
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be put to the HTTP endpoint
     * @param mediaType Media type of the HTTP put
     * @return a closeable response wrapping the streaming body
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpInputStreamResponse putForStream(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        return new HttpInputStreamResponse(execute(buildRequest("PUT", url, headers, body, mediaType), HttpResponse.BodyHandlers.ofInputStream()));
    }

    /**
     * Patch data at an HTTP endpoint, returning the response as a closeable stream.
     * <p>The returned {@link HttpInputStreamResponse} owns a live, connection-backed body stream and
     * <strong>must be closed</strong> (via try-with-resources) to release the connection.</p>
     *
     * @param url       URL endpoint where data will be sent as a patch
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param body      Body content to be patched to the HTTP endpoint
     * @param mediaType Media type of the HTTP patch
     * @return a closeable response wrapping the streaming body
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpInputStreamResponse patchForStream(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body, @NotNull String mediaType) throws HttpSendException {
        return new HttpInputStreamResponse(execute(buildRequest(PATCH_METHOD, url, headers, body, mediaType), HttpResponse.BodyHandlers.ofInputStream()));
    }

    /**
     * Delete data from an HTTP endpoint, returning the response as a closeable stream.
     * <p>The returned {@link HttpInputStreamResponse} owns a live, connection-backed body stream and
     * <strong>must be closed</strong> (via try-with-resources) to release the connection.</p>
     *
     * @param url       URL endpoint where data will be deleted from
     * @param headers   Map of key-value pairs for HTTP header fields and values
     * @param mediaType Media type of the HTTP delete
     * @return a closeable response wrapping the streaming body
     * @throws HttpSendException when the request cannot be sent
     */
    @NotNull
    public HttpInputStreamResponse deleteForStream(@NotNull String url, @NotNull Map<String, String> headers, @NotNull String mediaType) throws HttpSendException {
        return new HttpInputStreamResponse(execute(buildRequest(DELETE_METHOD, url, headers, null, mediaType), HttpResponse.BodyHandlers.ofInputStream()));
    }

    /**
     * A closeable view over an {@link HttpResponse}{@code <}{@link InputStream}{@code >}.
     *
     * <p>The response body is a live stream tied to the underlying HTTP connection. Closing this
     * object closes that stream and releases the connection, so callers should always use it in a
     * try-with-resources block:</p>
     * <pre>{@code
     * try (HttpInputStreamResponse response = httpService.postForStream(url, headers, body, mediaType)) {
     *     if (response.statusCode() == 200) {
     *         process(response.body());
     *     }
     * }
     * }</pre>
     * <p>Do not close the stream returned by {@link #body()} directly; close this wrapper instead.</p>
     */
    public static class HttpInputStreamResponse implements Closeable {
        private final HttpResponse<InputStream> response;

        /**
         * Wrap the given streaming HTTP response.
         * @param response the response whose body is a connection-backed {@link InputStream}
         */
        public HttpInputStreamResponse(@NotNull HttpResponse<InputStream> response) {
            this.response = response;
        }

        /**
         * @return the HTTP status code of the response
         */
        public int statusCode() {
            return response.statusCode();
        }

        /**
         * @return the response headers
         */
        public HttpHeaders headers() {
            return response.headers();
        }

        /**
         * The live response body stream. Remains valid until this wrapper is closed; do not close it
         * directly — close this {@link HttpInputStreamResponse} instead.
         * @return the response body stream
         */
        public InputStream body() {
            return response.body();
        }

        /**
         * The underlying HTTP response.
         * @return the wrapped {@link HttpResponse}
         */
        public HttpResponse<InputStream> response() {
            return response;
        }

        /**
         * Close the response body, releasing the underlying connection.
         * @throws IOException if closing the body stream fails
         */
        @Override
        public void close() throws IOException {
            response.body().close();
        }
    }
}
