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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes", "removal"}) // deprecation: these tests intentionally exercise the deprecated InputStream methods
class HttpServiceTest {
    private static final String ALTERNATE_CONTENT_TYPE = "xml";
    private static final String MEDIA_TYPE = "application/text";
    private static final String URL = "http://localhost:1234/any";
    private static final Map<String, String> HEADERS = Map.of("header1", "value1", "header2", "value2");
    private static final Map<String, String> HEADERS_WITH_OVERRIDE = Map.of("header1", "value1", "header2", "value2", "content-type", ALTERNATE_CONTENT_TYPE);
    @Mock
    HttpClient httpClient;
    @InjectMocks
    HttpService httpService;

    @Test
    void testPost() throws IOException, InterruptedException {
        HttpResponse<InputStream> httpResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        HttpResponse<InputStream> test = httpService.post(URL, HEADERS, targetStream, MEDIA_TYPE);
        verifyResponse("POST", httpResponse, test);
    }

    @Test
    void testPostOverrideContentType() throws IOException, InterruptedException {
        HttpResponse<InputStream> httpResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        HttpResponse<InputStream> test = httpService.post(URL, HEADERS_WITH_OVERRIDE, targetStream, MEDIA_TYPE);
        verifyResponse(ALTERNATE_CONTENT_TYPE, "POST", httpResponse, test);
    }

    @Test
    void testPostFailure() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Test IOException"), new InterruptedException("Test InterruptedException"));

        InputStream targetStream = new ByteArrayInputStream("body".getBytes());

        assertThatThrownBy(() -> httpService.post(URL, Map.of(), targetStream, MEDIA_TYPE))
                .hasMessage("IOException/POST: Test IOException");

        assertThatThrownBy(() -> httpService.post(URL, Map.of(), targetStream, MEDIA_TYPE))
                .hasMessage("InterruptedException/POST: Test InterruptedException");
    }

    @Test
    void testPut() throws IOException, InterruptedException {
        HttpResponse<InputStream> httpResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        HttpResponse<InputStream> test = httpService.put(URL, HEADERS, targetStream, MEDIA_TYPE);
        verifyResponse("PUT", httpResponse, test);
    }

    @Test
    void testPutFailure() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Test IOException"), new InterruptedException("Test InterruptedException"));

        InputStream targetStream = new ByteArrayInputStream("body".getBytes());

        assertThatThrownBy(() -> httpService.put(URL, Map.of(), targetStream, MEDIA_TYPE))
                .hasMessage("IOException/PUT: Test IOException");

        assertThatThrownBy(() -> httpService.put(URL, Map.of(), targetStream, MEDIA_TYPE))
                .hasMessage("InterruptedException/PUT: Test InterruptedException");
    }

    @Test
    void testPatch() throws IOException, InterruptedException {
        HttpResponse<InputStream> httpResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        HttpResponse<InputStream> test = httpService.patch(URL, HEADERS, targetStream, MEDIA_TYPE);
        verifyResponse("PATCH", httpResponse, test);
    }

    @Test
    void testPatchFailure() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Test IOException"), new InterruptedException("Test InterruptedException"));

        InputStream targetStream = new ByteArrayInputStream("body".getBytes());

        assertThatThrownBy(() -> httpService.patch(URL, Map.of(), targetStream, MEDIA_TYPE))
                .hasMessage("IOException/PATCH: Test IOException");

        assertThatThrownBy(() -> httpService.patch(URL, Map.of(), targetStream, MEDIA_TYPE))
                .hasMessage("InterruptedException/PATCH: Test InterruptedException");
    }

    @Test
    void testDelete() throws IOException, InterruptedException {
        HttpResponse<InputStream> httpResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        HttpResponse<InputStream> test = httpService.delete(URL, HEADERS, MEDIA_TYPE);
        verifyResponse("DELETE", httpResponse, test);
    }

    @Test
    void testDeleteFailure() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Test IOException"), new InterruptedException("Test InterruptedException"));

        assertThatThrownBy(() -> httpService.delete(URL, Map.of(), MEDIA_TYPE))
                .hasMessage("IOException/DELETE: Test IOException");

        assertThatThrownBy(() -> httpService.delete(URL, Map.of(), MEDIA_TYPE))
                .hasMessage("InterruptedException/DELETE: Test InterruptedException");
    }

    @Test
    void testPostForStream() throws IOException, InterruptedException {
        InputStream responseBody = Mockito.spy(new ByteArrayInputStream("response body".getBytes()));
        HttpResponse<InputStream> httpResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(httpResponse.statusCode()).thenReturn(201);
        Mockito.when(httpResponse.body()).thenReturn(responseBody);
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        try (HttpService.HttpInputStreamResponse response = httpService.postForStream(URL, HEADERS, targetStream, MEDIA_TYPE)) {
            assertEquals(201, response.statusCode());
            assertEquals("response body", new String(response.body().readAllBytes()));
            Mockito.verify(responseBody, Mockito.never()).close();
        }
        Mockito.verify(responseBody).close();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(httpClient).send(captor.capture(), Mockito.<HttpResponse.BodyHandler<InputStream>>any());
        assertEquals("POST", captor.getValue().method());
    }

    @Test
    void httpInputStreamResponseExposesLiveBodyThenClosesIt() throws IOException {
        InputStream stream = Mockito.spy(new ByteArrayInputStream("hello".getBytes()));
        HttpResponse<InputStream> httpResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(httpResponse.statusCode()).thenReturn(200);
        Mockito.when(httpResponse.body()).thenReturn(stream);

        try (HttpService.HttpInputStreamResponse response = new HttpService.HttpInputStreamResponse(httpResponse)) {
            assertEquals(200, response.statusCode());
            assertEquals(httpResponse, response.response());
            // body() returns the live, still-open stream (regression guard against closing it prematurely)
            assertEquals("hello", new String(response.body().readAllBytes()));
            Mockito.verify(stream, Mockito.never()).close();
        }
        // close() releases the underlying stream/connection
        Mockito.verify(stream).close();
    }

    /**
     * Invokes one of the connection-releasing convenience methods so the whole family — {@code *ForBytes}
     * (buffered {@code byte[]}) and {@code *DiscardingBody} (drained {@link Void}) — can be exercised by a
     * single parameterized test. The return type is wildcarded so both variants share one signature.
     */
    @FunctionalInterface
    private interface ReleasingCall {
        HttpResponse<?> apply(HttpService httpService, InputStream body) throws HttpSendException;
    }

    private static Stream<Arguments> releasingCalls() {
        return Stream.of(
                Arguments.of("postForBytes", "POST", (ReleasingCall) (svc, body) -> svc.postForBytes(URL, HEADERS, body, MEDIA_TYPE)),
                Arguments.of("putForBytes", "PUT", (ReleasingCall) (svc, body) -> svc.putForBytes(URL, HEADERS, body, MEDIA_TYPE)),
                Arguments.of("patchForBytes", "PATCH", (ReleasingCall) (svc, body) -> svc.patchForBytes(URL, HEADERS, body, MEDIA_TYPE)),
                Arguments.of("deleteForBytes", "DELETE", (ReleasingCall) (svc, body) -> svc.deleteForBytes(URL, HEADERS, MEDIA_TYPE)),
                Arguments.of("postDiscardingBody", "POST", (ReleasingCall) (svc, body) -> svc.postDiscardingBody(URL, HEADERS, body, MEDIA_TYPE)),
                Arguments.of("putDiscardingBody", "PUT", (ReleasingCall) (svc, body) -> svc.putDiscardingBody(URL, HEADERS, body, MEDIA_TYPE)),
                Arguments.of("patchDiscardingBody", "PATCH", (ReleasingCall) (svc, body) -> svc.patchDiscardingBody(URL, HEADERS, body, MEDIA_TYPE)),
                // DELETE has no request body; the supplied stream is ignored
                Arguments.of("deleteDiscardingBody", "DELETE", (ReleasingCall) (svc, body) -> svc.deleteDiscardingBody(URL, HEADERS, MEDIA_TYPE)));
    }

    /** The buffering subset — only the {@code *ForBytes} methods surface the response body as a {@code byte[]}. */
    private static Stream<Arguments> forBytesCalls() {
        return releasingCalls().filter(args -> ((String) args.get()[0]).endsWith("ForBytes"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releasingCalls")
    void releasingVariantSendsExpectedRequestAndReturnsResponse(String label, String method, ReleasingCall call) throws IOException, InterruptedException {
        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(httpResponse.statusCode()).thenReturn(200);
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        HttpResponse<?> test = call.apply(httpService, new ByteArrayInputStream("request body".getBytes()));

        assertEquals(200, test.statusCode());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(httpClient).send(captor.capture(), Mockito.any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertEquals(method, request.method());
        assertEquals(URL, request.uri().toString());
        Map<String, List<String>> headersMap = request.headers().map();
        assertEquals(MEDIA_TYPE, headersMap.get("content-type").getFirst());
        assertEquals("value1", headersMap.get("header1").getFirst());
        assertEquals("value2", headersMap.get("header2").getFirst());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releasingCalls")
    void releasingVariantFailureThrows(String label, String method, ReleasingCall call) throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Test IOException"), new InterruptedException("Test InterruptedException"));

        InputStream body = new ByteArrayInputStream("body".getBytes());

        assertThatThrownBy(() -> call.apply(httpService, body))
                .hasMessage("IOException/" + method + ": Test IOException");

        assertThatThrownBy(() -> call.apply(httpService, body))
                .hasMessage("InterruptedException/" + method + ": Test InterruptedException");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("forBytesCalls")
    void forBytesReturnsBufferedResponseBody(String label, String method, ReleasingCall call) throws IOException, InterruptedException {
        byte[] responseBytes = "response body".getBytes();
        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(httpResponse.body()).thenReturn(responseBytes);
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        HttpResponse<?> test = call.apply(httpService, new ByteArrayInputStream("request body".getBytes()));

        assertArrayEquals(responseBytes, (byte[]) test.body());
    }

    private void verifyResponse(String method, HttpResponse<InputStream> httpResponse, HttpResponse<InputStream> testResponse) throws IOException, InterruptedException {
        verifyResponse(MEDIA_TYPE, method, httpResponse, testResponse);
    }

    private void verifyResponse(String contentType, String method, HttpResponse<InputStream> httpResponse, HttpResponse<InputStream> testResponse) throws IOException, InterruptedException {
        ArgumentCaptor<HttpRequest> httpRequestArgumentCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(httpClient).send(httpRequestArgumentCaptor.capture(), Mockito.<HttpResponse.BodyHandler<InputStream>>any());

        HttpRequest httpRequest = httpRequestArgumentCaptor.getValue();
        assertEquals(method, httpRequest.method());
        assertEquals(URL, httpRequest.uri().toString());
        Map<String, List<String>> headersMap = httpRequest.headers().map();
        assertEquals(3, headersMap.size());
        assertEquals(1, headersMap.get("content-type").size());
        assertEquals(contentType, headersMap.get("content-type").getFirst());
        assertEquals(1, headersMap.get("header1").size());
        assertEquals("value1", headersMap.get("header1").getFirst());
        assertEquals(1, headersMap.get("header2").size());
        assertEquals("value2", headersMap.get("header2").getFirst());

        assertEquals(httpResponse, testResponse);

    }
}
