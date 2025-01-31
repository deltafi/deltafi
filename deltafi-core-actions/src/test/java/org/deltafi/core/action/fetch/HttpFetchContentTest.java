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

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.types.Content;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpFetchContentTest {
    private static final String URL_CONTEXT = "/fetch-test";
    private static final String TEST_FILE = "test.txt";
    private static final String FILE_CONTENT = "This is test content.";

    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("HttpFetchContentTest");

    @RegisterExtension
    static WireMockExtension wireMockHttp = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private HttpFetchContent action;

    @BeforeEach
    public void beforeEach() {
        wireMockHttp.resetMappings();
        action = new HttpFetchContent(HttpClient.newHttpClient());
    }

    @Test
    public void fetchesFileSuccessfully() {
        wireMockHttp.stubFor(get(URL_CONTEXT)
                .willReturn(ok()
                        .withHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + TEST_FILE + "\"")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                        .withBody(FILE_CONTENT)));

        ResultType result = executeFetchTest(true);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, TEST_FILE, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8))
                .hasTagsAt(0, Set.of("test-tag"));
    }

    @Test
    public void fetchesFileWithoutContentDisposition() {
        wireMockHttp.stubFor(get(URL_CONTEXT)
                .willReturn(ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                        .withBody(FILE_CONTENT)));

        ResultType result = executeFetchTest(true);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, HttpFetchContent.DEFAULT_FILENAME, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8))
                .hasTagsAt(0, Set.of("test-tag"));
    }

    @Test
    public void fetchesFileWithReplaceExistingFalse() {
        wireMockHttp.stubFor(get(URL_CONTEXT)
                .willReturn(ok()
                        .withHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + TEST_FILE + "\"")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                        .withBody(FILE_CONTENT)));

        ResultType result = executeFetchTest(false);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(1, TEST_FILE, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void returnsErrorOnBadResponse() {
        wireMockHttp.stubFor(get(URL_CONTEXT).willReturn(aResponse().withStatus(404)));

        ResultType result = executeFetchTest(true);

        ErrorResultAssert.assertThat(result)
                .hasCause("HTTP request failed with response code 404")
                .addedAnnotation("http_response", "404");
    }

    @Test
    public void testFetchWithAnnotations() {
        wireMockHttp.stubFor(get(URL_CONTEXT)
                .willReturn(ok()
                        .withHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + TEST_FILE + "\"")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                        .withBody(FILE_CONTENT)));

        ResultType result = executeFetchTestWithAnnotations("http_status");

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, TEST_FILE, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8))
                .addedAnnotation("http_status", "200");
    }

    private ResultType executeFetchTest(boolean replaceExisting) {
        return executeFetchTestWithAnnotations("http_response", replaceExisting);
    }

    @SuppressWarnings("SameParameterValue")
    private ResultType executeFetchTestWithAnnotations(String annotationName) {
        return executeFetchTestWithAnnotations(annotationName, true);
    }

    private ResultType executeFetchTestWithAnnotations(String annotationName, boolean replaceExisting) {
        WireMockRuntimeInfo wmRuntimeInfo = wireMockHttp.getRuntimeInfo();

        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setUrl(wmRuntimeInfo.getHttpBaseUrl() + URL_CONTEXT);
        parameters.setReplaceExistingContent(replaceExisting);
        parameters.setTags(Set.of("test-tag"));
        parameters.setResponseCodeAnnotationName(annotationName);

        ActionContent existingContent = new ActionContent(new Content("old", "old/old", List.of()), null);
        return action.transform(runner.actionContext(), parameters, TransformInput.builder().content(List.of(existingContent)).build());
    }

    @Test
    public void testHttpMethodSelection() {
        wireMockHttp.stubFor(post(URL_CONTEXT)
                .willReturn(ok()
                        .withHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + TEST_FILE + "\"")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                        .withBody(FILE_CONTENT)));

        ResultType result = executeFetchTestWithMethod("POST");

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, TEST_FILE, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8));

        // Verify WireMock received a POST request
        wireMockHttp.verify(postRequestedFor(urlEqualTo(URL_CONTEXT)));
    }

    @SuppressWarnings("SameParameterValue")
    private ResultType executeFetchTestWithMethod(String httpMethod) {
        WireMockRuntimeInfo wmRuntimeInfo = wireMockHttp.getRuntimeInfo();

        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setUrl(wmRuntimeInfo.getHttpBaseUrl() + URL_CONTEXT);
        parameters.setHttpMethod(httpMethod);

        return action.transform(runner.actionContext(), parameters, TransformInput.builder().build());
    }

    @Test
    public void testFetchWithRequestBody() {
        wireMockHttp.stubFor(post(URL_CONTEXT)
                .withRequestBody(equalTo("test request body"))
                .willReturn(ok()
                        .withHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + TEST_FILE + "\"")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                        .withBody(FILE_CONTENT)));

        ResultType result = executeFetchTestWithRequestBody("POST", "test request body");

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, TEST_FILE, MediaType.TEXT_PLAIN, FILE_CONTENT.getBytes(StandardCharsets.UTF_8));

        wireMockHttp.verify(postRequestedFor(urlEqualTo(URL_CONTEXT))
                .withRequestBody(equalTo("test request body")));
    }

    @SuppressWarnings("SameParameterValue")
    private ResultType executeFetchTestWithRequestBody(String httpMethod, String requestBody) {
        WireMockRuntimeInfo wmRuntimeInfo = wireMockHttp.getRuntimeInfo();

        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setUrl(wmRuntimeInfo.getHttpBaseUrl() + URL_CONTEXT);
        parameters.setHttpMethod(httpMethod);
        parameters.setRequestBody(requestBody);

        return action.transform(runner.actionContext(), parameters, TransformInput.builder().build());
    }

    @Test
    public void testFetchStoresResponseHeadersInMetadata() {
        wireMockHttp.stubFor(get(URL_CONTEXT)
                .willReturn(ok()
                        .withHeader("x-custom-header", "HeaderValue")
                        .withBody(FILE_CONTENT)));

        ResultType result = executeFetchTestWithMetadataKey("response_headers");

        TransformResultAssert.assertThat(result)
                .hasContentCount(1);
        assertTrue(((TransformResult)result).getMetadata().containsKey("response_headers"));
        assertTrue(((TransformResult)result).getMetadata().get("response_headers").contains("x-custom-header: HeaderValue"));
    }

    @SuppressWarnings("SameParameterValue")
    private ResultType executeFetchTestWithMetadataKey(String metadataKey) {
        WireMockRuntimeInfo wmRuntimeInfo = wireMockHttp.getRuntimeInfo();

        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setUrl(wmRuntimeInfo.getHttpBaseUrl() + URL_CONTEXT);
        parameters.setResponseHeadersMetadataKey(metadataKey);

        return action.transform(runner.actionContext(), parameters, TransformInput.builder().build());
    }

    @Test
    public void testFetchSendsExtraHeaders() {
        wireMockHttp.stubFor(get(URL_CONTEXT)
                .withHeader("X-Test-Header", equalTo("TestValue"))
                .willReturn(ok()
                        .withBody(FILE_CONTENT)));

        ResultType result = executeFetchTestWithExtraHeaders(Map.of("X-Test-Header", "TestValue"));

        TransformResultAssert.assertThat(result)
                .hasContentCount(1);

        wireMockHttp.verify(getRequestedFor(urlEqualTo(URL_CONTEXT))
                .withHeader("X-Test-Header", equalTo("TestValue")));
    }

    private ResultType executeFetchTestWithExtraHeaders(Map<String, String> requestHeaders) {
        WireMockRuntimeInfo wmRuntimeInfo = wireMockHttp.getRuntimeInfo();

        HttpFetchContentParameters parameters = new HttpFetchContentParameters();
        parameters.setUrl(wmRuntimeInfo.getHttpBaseUrl() + URL_CONTEXT);
        parameters.setRequestHeaders(requestHeaders);

        return action.transform(runner.actionContext(), parameters, TransformInput.builder().build());
    }
}