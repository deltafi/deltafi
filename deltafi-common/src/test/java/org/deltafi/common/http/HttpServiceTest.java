/*
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
    @Mock
    HttpClient httpClient;

    @InjectMocks
    HttpService httpService;

    @Test
    @SuppressWarnings("unchecked")
    void testPost() throws IOException, InterruptedException {
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
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        String url = "http://localhost:1234/any";
        Map<String, String> headers = Map.of("header1", "value1", "header2", "value2");
        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        HttpResponse<InputStream> test = httpService.post(url, headers, targetStream, "application/text");

        ArgumentCaptor<HttpRequest> httpRequestArgumentCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(httpClient).send(httpRequestArgumentCaptor.capture(), Mockito.any(HttpResponse.BodyHandler.class));

        HttpRequest httpRequest = httpRequestArgumentCaptor.getValue();
        assertEquals("POST", httpRequest.method());
        assertEquals(url, httpRequest.uri().toString());
        Map<String, List<String>> headersMap = httpRequest.headers().map();
        assertEquals(3, headersMap.size());
        assertEquals(1, headersMap.get("content-type").size());
        assertEquals("application/text", headersMap.get("content-type").get(0));
        assertEquals(1, headersMap.get("header1").size());
        assertEquals("value1", headersMap.get("header1").get(0));
        assertEquals(1, headersMap.get("header2").size());
        assertEquals("value2", headersMap.get("header2").get(0));

        assertEquals(httpResponse, test);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPostFailure() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Test IOException"), new InterruptedException("Test InterruptedException"));

        String url = "http://localhost:1234/any";
        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());

        try {
            HttpResponse<InputStream> test = httpService.post(url, Map.of(), targetStream, "application/text");
            fail("RuntimeException not thrown for IOException");
        } catch (RuntimeException e) {
            assertEquals("IOException: Test IOException", e.getMessage());
        }

        try {
            HttpResponse<InputStream> test = httpService.post(url, Map.of(), targetStream, "application/text");
            fail("RuntimeException not thrown for InterruptedException");
        } catch (RuntimeException e) {
            assertEquals("InterruptedException: Test InterruptedException", e.getMessage());
        }
    }
}