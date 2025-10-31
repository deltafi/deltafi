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
package org.deltafi.core.action.fetch;

import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.types.Content;
import org.deltafi.core.action.HttpClientMocker;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

class HttpFetchContentTest {
    private static final String URL_CONTEXT = "https://somewhere/fetch-test";
    private static final String TEST_FILE = "test.txt";
    private static final String FILE_CONTENT = "This is test content.";

    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("HttpFetchContentTest");

    private final HttpClient httpClient = Mockito.mock(HttpClient.class);
    private final HttpFetchContent action = new HttpFetchContent(httpClient);
    private final HttpClientMocker httpClientMocker = new HttpClientMocker(httpClient, TEST_FILE);

    @Test
    @SneakyThrows
    void fetchesFileSuccessfully() {
        httpClientMocker.mockSuccessResponse();

        ResultType result = executeFetchTest(true);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, TEST_FILE, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8))
                .hasTagsAt(0, Set.of("test-tag"));

        httpClientMocker.verifyRequest(r -> Assertions.assertThat(r.method()).isEqualTo("GET"));
    }

    @Test
    void fetchesFileWithoutContentDisposition() {
        httpClientMocker.mockSuccessResponse(Map.of(CONTENT_TYPE, MediaType.TEXT_PLAIN));

        ResultType result = executeFetchTest(true);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, HttpFetchContent.DEFAULT_FILENAME, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8))
                .hasTagsAt(0, Set.of("test-tag"));
    }

    @Test
    void fetchesFileWithReplaceExistingFalse() {
        httpClientMocker.mockSuccessResponse();

        ResultType result = executeFetchTest(false);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(1, TEST_FILE, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void returnsErrorOnBadResponse() {
        HttpResponse<InputStream> response = httpClientMocker.mockResponse(404);
        httpClientMocker.mockResponseBody(response, "HTTP request failed with response code 404");

        ResultType result = executeFetchTest(true);

        ErrorResultAssert.assertThat(result)
                .hasCause("HTTP request failed with response code 404")
                .addedAnnotation("http_response", "404");
    }

    @Test
    void testFetchWithAnnotations() {
        httpClientMocker.mockSuccessResponse();

        ResultType result = executeFetchTestWithAnnotations("http_status");

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, TEST_FILE, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8))
                .addedAnnotation("http_status", "200");
    }

    @Test
    @SneakyThrows
    void testHttpMethodSelection() {
        httpClientMocker.mockSuccessResponse();
        ResultType result = executeFetchTestWithMethod("POST");

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, TEST_FILE, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8));

        httpClientMocker.verifyRequest(r -> Assertions.assertThat(r.method()).isEqualTo("POST"));
    }

    @SuppressWarnings("SameParameterValue")
    private ResultType executeFetchTestWithMethod(String httpMethod) {
        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setUrl(URL_CONTEXT);
        parameters.setHttpMethod(httpMethod);

        return action.transform(runner.actionContext(), parameters, TransformInput.builder().build());
    }

    @Test
    @SneakyThrows
    void testFetchWithRequestBody() {
        httpClientMocker.mockSuccessResponse();

        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setHttpMethod("POST");
        parameters.setRequestBody("test request body");
        ResultType result = executeFetch(parameters);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, TEST_FILE, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8));

        httpClientMocker.verifyRequest(this::verifyPost);
    }

    private void verifyPost(HttpRequest httpRequest) {
        Assertions.assertThat(httpRequest.method()).isEqualTo("POST");
        String requestBody = httpClientMocker.readRequestBody(httpRequest);
        Assertions.assertThat(requestBody).isEqualTo("test request body");
    }

    @Test
    void testFetchStoresResponseHeadersInMetadata() {
        httpClientMocker.mockSuccessResponse(Map.of("x-custom-header", "HeaderValue"));

        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setResponseHeadersMetadataKey("response_headers");
        ResultType result = executeFetch(parameters);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .addedMetadata("response_headers", "x-custom-header: HeaderValue");
    }

    @Test
    @SneakyThrows
    void testFetchSendsExtraHeaders() {
        httpClientMocker.mockSuccessResponse();

        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setRequestHeaders(Map.of("X-Test-Header", "TestValue"));
        ResultType result = executeFetch(parameters);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1);

        httpClientMocker.verifyRequest(this::verifyHeader);
    }

    private void verifyHeader(HttpRequest httpRequest) {
        Assertions.assertThat(httpRequest.headers().firstValue("X-Test-Header").orElseThrow()).isEqualTo("TestValue");
    }

    @Test
    void testFetchStoresResponseIndividualHeadersInMetadata() {
        Map<String, List<String>> headerMap = toMapOfList(Map.of("x-custom-header", "HeaderValue","x-b", "value", "x-c", "value"));
        headerMap.put("x-a", List.of("value", "value2"));
        HttpHeaders headers = HttpHeaders.of(headerMap, (a,b)-> true);

        HttpResponse<InputStream> response = httpClientMocker.mockResponse(200);
        Mockito.when(response.headers()).thenReturn(headers);
        httpClientMocker.mockResponseBody(response);

        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setHeadersToMetadata(List.of("x-a", "x-c", "non-existent"));

        ResultType result = executeFetch(parameters);

        TransformResultAssert.assertThat(result)
                .addedMetadata("x-a", "value, value2")
                .addedMetadata("x-c", "value");

        Map<String, String> metadata = ((TransformResult) result).getMetadata();
        Assertions.assertThat(metadata).doesNotContainKey("x-custom-header").doesNotContainKey("x-b").doesNotContainKey("non-existent");
    }

    @Test
    void testFetchStoreFilenameInMetadata() {
        httpClientMocker.mockSuccessResponse();

        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setFilenameMetadataKey("response-filename");

        ResultType result = executeFetch(parameters);

        TransformResultAssert.assertThat(result)
                .addedMetadata("response-filename", TEST_FILE);
    }

    @Test
    void testFetchStoreFilenameInMetadata_missingFilename() {
        // response headers will not include CONTENT_DISPOSITION
        httpClientMocker.mockSuccessResponse(Map.of());

        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setFilenameMetadataKey("response-filename");

        Map<String, String> metadata = ((TransformResult) executeFetch(parameters)).getMetadata();
        Assertions.assertThat(metadata).doesNotContainKey("response-filename");
    }

    private ResultType executeFetchTest(boolean replaceExisting) {
        return executeFetchTestWithAnnotations("http_response", replaceExisting);
    }

    @SuppressWarnings("SameParameterValue")
    private ResultType executeFetchTestWithAnnotations(String annotationName) {
        return executeFetchTestWithAnnotations(annotationName, true);
    }

    private ResultType executeFetchTestWithAnnotations(String annotationName, boolean replaceExisting) {
        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setUrl(URL_CONTEXT);
        parameters.setReplaceExistingContent(replaceExisting);
        parameters.setTags(Set.of("test-tag"));
        parameters.setResponseCodeAnnotationName(annotationName);

        ActionContent existingContent = new ActionContent(new Content("old", "old/old", List.of()), null);
        return action.transform(runner.actionContext(), parameters, TransformInput.builder().content(List.of(existingContent)).build());
    }

    private ResultType executeFetch(HttpFetchContentParameters parameters) {
        parameters.setUrl(URL_CONTEXT);
        return action.transform(runner.actionContext(), parameters, TransformInput.builder().build());
    }

    private Map<String, List<String>> toMapOfList(Map<String, String> headersMap) {
        Map<String, List<String>> headers = new HashMap<>();
        headersMap.forEach((key, value) -> headers.put(key, List.of(value)));
        return headers;
    }
}