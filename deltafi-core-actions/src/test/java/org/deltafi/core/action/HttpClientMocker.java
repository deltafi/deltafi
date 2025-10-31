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
package org.deltafi.core.action;

import lombok.SneakyThrows;
import org.mockito.Mockito;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Consumer;

import static javax.ws.rs.core.HttpHeaders.CONTENT_DISPOSITION;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

public class HttpClientMocker {
    private static final String FILE_CONTENT = "This is test content.";

    private final HttpClient httpClient;
    private final String expectedFilename;

    public HttpClientMocker(HttpClient httpClient, String expectedFilename) {
        this.httpClient = httpClient;
        this.expectedFilename = expectedFilename;
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public HttpResponse<InputStream> mockResponse(int statusCode) {
        HttpResponse<InputStream> response = Mockito.mock(HttpResponse.class);
        Mockito.when(response.statusCode()).thenReturn(statusCode);
        Mockito.when(httpClient.send(Mockito.any(HttpRequest.class), Mockito.eq(HttpResponse.BodyHandlers.ofInputStream())))
                .thenReturn(response);
        return response;
    }

    public void mockSuccessResponse() {
        mockSuccessResponse(Map.of(CONTENT_DISPOSITION, "attachment; filename=\"" + expectedFilename + "\"",
                CONTENT_TYPE, MediaType.TEXT_PLAIN));
    }

    @SneakyThrows
    public void mockSuccessResponse(Map<String, String> responseHeaders) {
        HttpResponse<InputStream> response = mockResponse(200);
        Mockito.when(response.headers()).thenReturn(headers(responseHeaders));
        mockResponseBody(response);
    }

    public void mockResponseBody(HttpResponse<InputStream> response) {
        mockResponseBody(response, FILE_CONTENT);
    }

    public void mockResponseBody(HttpResponse<InputStream> response, String body) {
        Mockito.when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    public java.net.http.HttpHeaders headers(Map<String, String> headersMap) {
        return java.net.http.HttpHeaders.of(toMapOfList(headersMap), (x,y) -> true);
    }

    public Map<String, List<String>> toMapOfList(Map<String, String> headersMap) {
        Map<String, List<String>> headers = new HashMap<>();
        headersMap.forEach((key, value) -> headers.put(key, List.of(value)));
        return headers;
    }

    @SneakyThrows
    public void verifyRequest(Consumer<HttpRequest> httpRequestAssertions) {
        Mockito.verify(httpClient).send(Mockito.assertArg(httpRequestAssertions), Mockito.eq(HttpResponse.BodyHandlers.ofInputStream()));

    }

    public String readRequestBody(HttpRequest httpRequest) {
        RequestBodyReader requestBodyReader = new RequestBodyReader();
        httpRequest.bodyPublisher().orElseThrow().subscribe(requestBodyReader);
        return requestBodyReader.getBodyAsString();
    }

    private static class RequestBodyReader implements Flow.Subscriber<ByteBuffer> {

        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final CompletableFuture<Void> completion = new CompletableFuture<>();

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            completion.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            completion.complete(null);
        }

        public String getBodyAsString() {
            completion.join();
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }
}
