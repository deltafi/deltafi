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
package org.deltafi.core.action.egress;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HttpEgressBaseTest {
    private static class TestHttpEgress extends HttpEgressBase<HttpEgressParameters> {
        public TestHttpEgress(OkHttpClient httpClient) {
            super("Test HTTP egress", httpClient);
        }

        @Override
        protected Map<String, String> buildHeaders(@NotNull ActionContext context, @NotNull HttpEgressParameters params,
                @NotNull EgressInput input) {
            return Map.of("header-1", "value-1", "header-2", "value-2");
        }
    }

    private static final String URL_CONTEXT = "/endpoint";
    private static final String CONTENT = "This is the test content.";

    private final OkHttpClient httpService = new OkHttpClient();
    private final TestHttpEgress action = new TestHttpEgress(httpService);
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("RestPostEgressTest");

    @RegisterExtension
    static WireMockExtension wireMockHttp = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort().http2PlainDisabled(true))
            .build();

    @BeforeEach
    public void beforeEach() {
        wireMockHttp.resetAll();
    }

    @Test
    void egresses() {
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader(HttpHeaders.CONTENT_LENGTH, WireMock.equalTo(String.valueOf(CONTENT.length())))
                .withHeader("header-1", WireMock.equalTo("value-1"))
                .withHeader("header-2", WireMock.equalTo("value-2"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .willReturn(WireMock.ok()));

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        params.setUrl(url);
        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(EgressResult.class, egressResultType);
    }

    @Test
    void errors() {
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader(HttpHeaders.CONTENT_LENGTH, WireMock.equalTo(String.valueOf(CONTENT.length())))
                .withHeader("header-1", WireMock.equalTo("value-1"))
                .withHeader("header-2", WireMock.equalTo("value-2"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .willReturn(WireMock.serverError().withBody("There was an error!")));

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        params.setUrl(url);
        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(ErrorResult.class, egressResultType);
        assertEquals("Unsuccessful HTTP POST: 500", ((ErrorResult) egressResultType).getErrorCause());
        assertEquals("There was an error!", ((ErrorResult) egressResultType).getErrorContext());
    }

    @Test
    void errorsOnHttpPostException() {
        OkHttpClient mockHttpService = Mockito.mock(OkHttpClient.class);
        Mockito.when(mockHttpService.newCall(Mockito.any()))
                .thenThrow(new RuntimeException());

        TestHttpEgress testAction = new TestHttpEgress(mockHttpService);

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        params.setUrl(url);
        EgressResultType egressResultType = testAction.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(ErrorResult.class, egressResultType);
        assertEquals("Unexpected error during POST request", ((ErrorResult) egressResultType).getErrorCause());
    }

    private static class IOExceptionOpeningInputStream extends TestHttpEgress {
        public IOExceptionOpeningInputStream(OkHttpClient httpClient) {
            super(httpClient);
        }

        @Override
        protected RequestBody prepareRequestBody(@NotNull ActionContext context, @NotNull EgressInput input){
            return new RequestBody() {

                @Override
                public void writeTo(@NotNull BufferedSink bufferedSink) throws IOException {
                    throw new IOException();
                }

                @Override
                public @Nullable okhttp3.MediaType contentType() {
                    return null;
                }
            };
        }
    }

    @Test
    void errorsOnIoExceptionOpeningInputStream() {
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader(HttpHeaders.CONTENT_LENGTH, WireMock.equalTo(String.valueOf(CONTENT.length())))
                .withHeader("header-1", WireMock.equalTo("value-1"))
                .withHeader("header-2", WireMock.equalTo("value-2"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .willReturn(WireMock.ok()));

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        params.setUrl(url);
        IOExceptionOpeningInputStream testAction = new IOExceptionOpeningInputStream(httpService);
        EgressResultType egressResultType = testAction.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(ErrorResult.class, egressResultType);
        assertEquals("Service POST failure", ((ErrorResult) egressResultType).getErrorCause());
    }

    @Test
    void handlesNullContentWithDefaultPolicy() {
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .willReturn(WireMock.aResponse().withStatus(200)));

        EgressInput egressInput = EgressInput.builder()
                .content(null)  // null content
                .metadata(Map.of("test", "value"))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        params.setUrl(url);
        // Default policy is ERROR
        
        TestHttpEgress testAction = new TestHttpEgress(httpService);
        EgressResultType egressResultType = testAction.egress(runner.actionContext(), params, egressInput);

        // Should return ErrorResult with default ERROR policy
        assertInstanceOf(ErrorResult.class, egressResultType);
        assertEquals("Cannot perform egress: no content available", ((ErrorResult) egressResultType).getErrorCause());
    }

    @Test
    void handlesNullContentWithFilterPolicy() {
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .willReturn(WireMock.aResponse().withStatus(200)));

        EgressInput egressInput = EgressInput.builder()
                .content(null)  // null content
                .metadata(Map.of("test", "value"))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        params.setUrl(url);
        params.setNoContentPolicy(NoContentPolicy.FILTER);
        
        TestHttpEgress testAction = new TestHttpEgress(httpService);
        EgressResultType egressResultType = testAction.egress(runner.actionContext(), params, egressInput);

        // Should return FilterResult
        assertInstanceOf(FilterResult.class, egressResultType);
        assertEquals("Content is null - filtered by noContentPolicy", ((FilterResult) egressResultType).getFilteredCause());
    }

    @Test
    void handlesNullContentWithErrorPolicy() {
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .willReturn(WireMock.aResponse().withStatus(200)));

        EgressInput egressInput = EgressInput.builder()
                .content(null)  // null content
                .metadata(Map.of("test", "value"))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        params.setUrl(url);
        params.setNoContentPolicy(NoContentPolicy.ERROR);
        
        TestHttpEgress testAction = new TestHttpEgress(httpService);
        EgressResultType egressResultType = testAction.egress(runner.actionContext(), params, egressInput);

        // Should return ErrorResult
        assertInstanceOf(ErrorResult.class, egressResultType);
        assertEquals("Cannot perform egress: no content available", ((ErrorResult) egressResultType).getErrorCause());
    }

    @Test
    void handlesNullContentWithSendEmptyPolicy() {
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .willReturn(WireMock.aResponse().withStatus(200)));

        EgressInput egressInput = EgressInput.builder()
                .content(null)  // null content
                .metadata(Map.of("test", "value"))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        params.setUrl(url);
        params.setNoContentPolicy(NoContentPolicy.SEND_EMPTY);
        
        TestHttpEgress testAction = new TestHttpEgress(httpService);
        EgressResultType egressResultType = testAction.egress(runner.actionContext(), params, egressInput);

        // Should succeed with zero data sent
        assertInstanceOf(EgressResult.class, egressResultType);
    }
}
