/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.http.HttpPostException;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.Map;

import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.deltafi.common.constant.DeltaFiConstants.BYTES_OUT;
import static org.deltafi.common.constant.DeltaFiConstants.FILES_OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class HttpEgressBaseTest {
    private static class TestHttpEgress extends HttpEgressBase<HttpEgressParameters> {
        public TestHttpEgress(HttpService httpService) {
            super("Test HTTP egress", httpService);
        }

        @Override
        protected Map<String, String> buildHeaders(@NotNull ActionContext context, @NotNull HttpEgressParameters params,
                @NotNull EgressInput input) {
            return Map.of("header-1", "value-1", "header-2", "value-2");
        }
    }

    private static final String URL_CONTEXT = "/endpoint";
    private static final String CONTENT = "This is the test content.";

    private final HttpService httpService = new HttpService(HttpClient.newHttpClient());
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
    public void egresses() {
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
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
        assertEquals(FILES_OUT, ((EgressResult) egressResultType).getMetrics().getFirst().getName());
        assertEquals(1, ((EgressResult) egressResultType).getMetrics().getFirst().getValue());
        assertEquals(url, ((EgressResult) egressResultType).getMetrics().getFirst().getTags().get("endpoint"));
        assertEquals(BYTES_OUT, ((EgressResult) egressResultType).getMetrics().getLast().getName());
        assertEquals(CONTENT.length(), ((EgressResult) egressResultType).getMetrics().getLast().getValue());
        assertEquals(url, ((EgressResult) egressResultType).getMetrics().getLast().getTags().get("endpoint"));
    }

    @Test
    public void egressesAfterRetry() {
        // First POST fails with status 999
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader("header-1", WireMock.equalTo("value-1"))
                .withHeader("header-2", WireMock.equalTo("value-2"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .inScenario("test").whenScenarioStateIs(STARTED)
                .willReturn(WireMock.status(999))
                .willSetStateTo("succeed"));

        // Second POST succeeds
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader("header-1", WireMock.equalTo("value-1"))
                .withHeader("header-2", WireMock.equalTo("value-2"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .inScenario("test").whenScenarioStateIs("succeed")
                .willReturn(WireMock.ok()));

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        params.setUrl(url);
        params.setRetryCount(1);
        params.setRetryDelayMs(0);
        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(EgressResult.class, egressResultType);
        assertEquals(FILES_OUT, ((EgressResult) egressResultType).getMetrics().getFirst().getName());
        assertEquals(1, ((EgressResult) egressResultType).getMetrics().getFirst().getValue());
        assertEquals(url, ((EgressResult) egressResultType).getMetrics().getFirst().getTags().get("endpoint"));
        assertEquals(BYTES_OUT, ((EgressResult) egressResultType).getMetrics().getLast().getName());
        assertEquals(CONTENT.length(), ((EgressResult) egressResultType).getMetrics().getLast().getValue());
        assertEquals(url, ((EgressResult) egressResultType).getMetrics().getLast().getTags().get("endpoint"));
    }

    @Test
    public void errorsAfterMaxRetries() {
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
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
        params.setRetryCount(1);
        params.setRetryDelayMs(0);
        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(ErrorResult.class, egressResultType);
        assertEquals("Unsuccessful HTTP POST: 500", ((ErrorResult) egressResultType).getErrorCause());
        assertEquals("There was an error!", ((ErrorResult) egressResultType).getErrorContext());
    }

    @Test
    public void errorsOnHttpPostException() {
        HttpService mockHttpService = Mockito.mock(HttpService.class);
        Mockito.when(mockHttpService.post(Mockito.eq(wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT),
                        Mockito.anyMap(), Mockito.any(), Mockito.anyString()))
                .thenThrow(new HttpPostException("class", "post exception"));

        TestHttpEgress action = new TestHttpEgress(mockHttpService);

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        params.setUrl(url);
        params.setRetryCount(1);
        params.setRetryDelayMs(0);
        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(ErrorResult.class, egressResultType);
        assertEquals("Service post failure", ((ErrorResult) egressResultType).getErrorCause());
    }

    private static class IOExceptionOpeningInputStream extends TestHttpEgress {
        public IOExceptionOpeningInputStream(HttpService httpService) {
            super(httpService);
        }

        @Override
        protected InputStream openInputStream(@NotNull ActionContext context, @NotNull EgressInput input)
                throws IOException {
            throw new IOException();
        }
    }

    @Test
    void errorsOnIoExceptionOpeningInputStream() {
        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
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
        IOExceptionOpeningInputStream action = new IOExceptionOpeningInputStream(httpService);
        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(ErrorResult.class, egressResultType);
        assertEquals("Unable to open input stream", ((ErrorResult) egressResultType).getErrorCause());
    }
}
