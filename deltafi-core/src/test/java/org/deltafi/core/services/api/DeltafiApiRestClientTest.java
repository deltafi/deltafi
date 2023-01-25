/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services.api;

import lombok.SneakyThrows;
import org.deltafi.core.services.api.model.DiskMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

final class StringSubscriber implements Flow.Subscriber<ByteBuffer> {
    final HttpResponse.BodySubscriber<String> wrapped;
    StringSubscriber(HttpResponse.BodySubscriber<String> wrapped) {
        this.wrapped = wrapped;
    }
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        wrapped.onSubscribe(subscription);
    }
    @Override
    public void onNext(ByteBuffer item) { wrapped.onNext(List.of(item)); }
    @Override
    public void onError(Throwable throwable) { wrapped.onError(throwable); }
    @Override
    public void onComplete() { wrapped.onComplete(); }
}

final class HttpRequestHelper {
    static String extractBody(HttpRequest request) {
        // This is actually how hard it is to get a string body out of an HttpRequest...
        return request.bodyPublisher().map(bp -> {
            HttpResponse.BodySubscriber<String> bs = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
            StringSubscriber ss = new StringSubscriber(bs);
            bp.subscribe(ss);
            return bs.getBody().toCompletableFuture().join();
        }).orElseThrow();
    }
}

@ExtendWith(MockitoExtension.class)
class DeltafiApiRestClientTest {
    DeltafiApiRestClient deltafiApiRestClient = new DeltafiApiRestClient("https://url.com");

    @Mock
    HttpClient httpClient;

    @Test
    void testContentMetrics() throws IOException, InterruptedException {
        ReflectionTestUtils.setField(deltafiApiRestClient, "httpClient", httpClient);
        HttpResponse<Object> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"content\":{\"limit\":107362627584,\"usage\":28682784768},\"timestamp\":\"2022-05-17 14:37:28 -0400\"}");
        when(httpClient.send(any(), any())).thenReturn(response);
        DiskMetrics diskMetrics = deltafiApiRestClient.contentMetrics();
        assertEquals(107362627584L, diskMetrics.getLimit());
        assertEquals(28682784768L, diskMetrics.getUsage());
        assertEquals(26.72, (double) Math.round(diskMetrics.percentUsed() * 100) / 100);
    }

    @SneakyThrows
    @Test
    void testCreateEvent() {
        final String REQUEST_BODY = """
                {
                    "summary": "whee!"
                }""";
        final String RESPONSE_BODY = """
                {
                    "content": "whee!"
                }""";

        AtomicReference<HttpRequest> request = new AtomicReference<>(null);

        ReflectionTestUtils.setField(deltafiApiRestClient, "httpClient", httpClient);
        HttpResponse<Object> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(RESPONSE_BODY);
        when(httpClient.send(any(), any())).then(invocation -> {
            request.set(invocation.getArgument(0, HttpRequest.class));
            return response;
        });

        String responseBody = deltafiApiRestClient.createEvent(REQUEST_BODY);
        Mockito.verify(httpClient).send(any(), any());
        Mockito.verifyNoMoreInteractions(httpClient);

        assertThat( HttpRequestHelper.extractBody(request.get()), equalTo(REQUEST_BODY));
        assertThat( request.toString(), equalTo("https://url.com/api/v1/events POST"));
        assertThat( request.get().headers().map(), equalTo(Map.of(
                "accept", List.of(MediaType.APPLICATION_JSON.toString()),
                "X-User-Permissions", List.of("Admin")
        )));
        assertThat( responseBody, equalTo(RESPONSE_BODY));
    }

    @SneakyThrows
    @Test
    void testCreateEventUnreachable() {
        final String REQUEST_BODY = """
                {
                    "summary": "whee!"
                }""";
        final String RESPONSE_BODY = "Narf!";

        AtomicReference<HttpRequest> request = new AtomicReference<>(null);

        ReflectionTestUtils.setField(deltafiApiRestClient, "httpClient", httpClient);
        HttpResponse<Object> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(418);
        when(response.body()).thenReturn(RESPONSE_BODY);
        when(httpClient.send(any(), any())).then(invocation -> {
            request.set(invocation.getArgument(0, HttpRequest.class));
            return response;
        });

        String responseBody = deltafiApiRestClient.createEvent(REQUEST_BODY);
        Mockito.verify(httpClient).send(any(), any());
        Mockito.verifyNoMoreInteractions(httpClient);

        assertThat( HttpRequestHelper.extractBody(request.get()), equalTo(REQUEST_BODY));
        assertThat( request.toString(), equalTo("https://url.com/api/v1/events POST"));
        assertThat( request.get().headers().map(), equalTo(Map.of(
                "accept", List.of(MediaType.APPLICATION_JSON.toString()),
                "X-User-Permissions", List.of("Admin")
        )));
        assertThat( responseBody, equalTo(RESPONSE_BODY));
    }
}
