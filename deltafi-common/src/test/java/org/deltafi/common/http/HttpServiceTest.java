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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(MockitoExtension.class)
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
    @SuppressWarnings("unchecked")
    void testPost() throws IOException, InterruptedException {
        HttpResponse<InputStream> httpResponse = makeHttpResponse();
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        HttpResponse<InputStream> test = httpService.post(URL, HEADERS, targetStream, MEDIA_TYPE);
        verifyResponse("POST", httpResponse, test);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPostOverrideContentType() throws IOException, InterruptedException {
        HttpResponse<InputStream> httpResponse = makeHttpResponse();
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        HttpResponse<InputStream> test = httpService.post(URL, HEADERS_WITH_OVERRIDE, targetStream, MEDIA_TYPE);
        verifyResponse(ALTERNATE_CONTENT_TYPE, "POST", httpResponse, test);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPostFailure() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Test IOException"), new InterruptedException("Test InterruptedException"));

        InputStream targetStream = new ByteArrayInputStream("body".getBytes());

        try {
            HttpResponse<InputStream> test = httpService.post(URL, Map.of(), targetStream, MEDIA_TYPE);
            fail("RuntimeException not thrown for IOException");
        } catch (RuntimeException e) {
            assertEquals("IOException/POST: Test IOException", e.getMessage());
        }

        try {
            HttpResponse<InputStream> test = httpService.post(URL, Map.of(), targetStream, MEDIA_TYPE);
            fail("RuntimeException not thrown for InterruptedException");
        } catch (RuntimeException e) {
            assertEquals("InterruptedException/POST: Test InterruptedException", e.getMessage());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPut() throws IOException, InterruptedException {
        HttpResponse<InputStream> httpResponse = makeHttpResponse();
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        HttpResponse<InputStream> test = httpService.put(URL, HEADERS, targetStream, MEDIA_TYPE);
        verifyResponse("PUT", httpResponse, test);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPutFailure() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Test IOException"), new InterruptedException("Test InterruptedException"));

        InputStream targetStream = new ByteArrayInputStream("body".getBytes());

        try {
            HttpResponse<InputStream> test = httpService.put(URL, Map.of(), targetStream, MEDIA_TYPE);
            fail("RuntimeException not thrown for IOException");
        } catch (RuntimeException e) {
            assertEquals("IOException/PUT: Test IOException", e.getMessage());
        }

        try {
            HttpResponse<InputStream> test = httpService.put(URL, Map.of(), targetStream, MEDIA_TYPE);
            fail("RuntimeException not thrown for InterruptedException");
        } catch (RuntimeException e) {
            assertEquals("InterruptedException/PUT: Test InterruptedException", e.getMessage());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPatch() throws IOException, InterruptedException {
        HttpResponse<InputStream> httpResponse = makeHttpResponse();
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        HttpResponse<InputStream> test = httpService.patch(URL, HEADERS, targetStream, MEDIA_TYPE);
        verifyResponse("PATCH", httpResponse, test);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPatchFailure() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Test IOException"), new InterruptedException("Test InterruptedException"));

        InputStream targetStream = new ByteArrayInputStream("body".getBytes());

        try {
            HttpResponse<InputStream> test = httpService.patch(URL, Map.of(), targetStream, MEDIA_TYPE);
            fail("RuntimeException not thrown for IOException");
        } catch (RuntimeException e) {
            assertEquals("IOException/PATCH: Test IOException", e.getMessage());
        }

        try {
            HttpResponse<InputStream> test = httpService.patch(URL, Map.of(), targetStream, MEDIA_TYPE);
            fail("RuntimeException not thrown for InterruptedException");
        } catch (RuntimeException e) {
            assertEquals("InterruptedException/PATCH: Test InterruptedException", e.getMessage());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDelete() throws IOException, InterruptedException {
        HttpResponse<InputStream> httpResponse = makeHttpResponse();
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        HttpResponse<InputStream> test = httpService.delete(URL, HEADERS, MEDIA_TYPE);
        verifyResponse("DELETE", httpResponse, test);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDeleteFailure() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Test IOException"), new InterruptedException("Test InterruptedException"));

        try {
            HttpResponse<InputStream> test = httpService.delete(URL, Map.of(), MEDIA_TYPE);
            fail("RuntimeException not thrown for IOException");
        } catch (RuntimeException e) {
            assertEquals("IOException/DELETE: Test IOException", e.getMessage());
        }

        try {
            HttpResponse<InputStream> test = httpService.delete(URL, Map.of(), MEDIA_TYPE);
            fail("RuntimeException not thrown for InterruptedException");
        } catch (RuntimeException e) {
            assertEquals("InterruptedException/DELETE: Test InterruptedException", e.getMessage());
        }
    }

    private HttpResponse<InputStream> makeHttpResponse() {
        HttpResponse<InputStream> httpResponse = new HttpResponse<>() {
            @Override
            public int statusCode() {
                return 0;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return null;
            }

            @Override
            public InputStream body() {
                return null;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return null;
            }

            @Override
            public HttpClient.Version version() {
                return null;
            }
        };
        return httpResponse;
    }

    private void verifyResponse(String method, HttpResponse<InputStream> httpResponse, HttpResponse<InputStream> testResponse) throws IOException, InterruptedException {
        verifyResponse(MEDIA_TYPE, method, httpResponse, testResponse);
    }

    private void verifyResponse(String contentType, String method, HttpResponse<InputStream> httpResponse, HttpResponse<InputStream> testResponse) throws IOException, InterruptedException {
        ArgumentCaptor<HttpRequest> httpRequestArgumentCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(httpClient).send(httpRequestArgumentCaptor.capture(), Mockito.any(HttpResponse.BodyHandler.class));

        HttpRequest httpRequest = httpRequestArgumentCaptor.getValue();
        assertEquals(method, httpRequest.method());
        assertEquals(URL, httpRequest.uri().toString());
        Map<String, List<String>> headersMap = httpRequest.headers().map();
        assertEquals(3, headersMap.size());
        assertEquals(1, headersMap.get("content-type").size());
        assertEquals(contentType, headersMap.get("content-type").get(0));
        assertEquals(1, headersMap.get("header1").size());
        assertEquals("value1", headersMap.get("header1").get(0));
        assertEquals(1, headersMap.get("header2").size());
        assertEquals("value2", headersMap.get("header2").get(0));

        assertEquals(httpResponse, testResponse);

    }
}
