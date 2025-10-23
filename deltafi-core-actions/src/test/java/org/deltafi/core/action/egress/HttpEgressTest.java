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
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.common.content.ActionContentStorageService;
import org.deltafi.common.test.content.InMemoryContentStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HttpEgressTest {
    private static final String URL_CONTEXT = "/endpoint";
    private static final String CONTENT = "This is the test content.";
    private static final ActionContentStorageService CONTENT_STORAGE_SERVICE =
            new InMemoryContentStorageService();
    @RegisterExtension
    static WireMockExtension wireMockHttp = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort().http2PlainDisabled(true))
            .build();
    private final HttpEgress action = new HttpEgress(new OkHttpClient());
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("HttpEgressTest");

    @BeforeEach
    void beforeEach() {
        wireMockHttp.resetAll();
    }

    @Test
    void egressDelete() {
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.delete(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader("headers-map", WireMock.equalTo("{\"dataSource\":\"test-data-source\"," +
                        "\"did\":\"" + did + "\",\"filename\":\"test-content\",\"flow\":\"test-flow-name\"," +
                        "\"key-1\":\"value-1\",\"key-2\":\"value-2\",\"originalFilename\":\"test-delta-file\"}"))
                .willReturn(WireMock.ok()));

        HttpEgress.Parameters params = makeParameters(HttpRequestMethod.DELETE);
        EgressResultType egressResultType = runTest(did, params);
        assertInstanceOf(EgressResult.class, egressResultType);
    }

    @Test
    void egressPost() {
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader(HttpHeaders.CONTENT_LENGTH, WireMock.matching(String.valueOf(CONTENT.length())))
                .withHeader("headers-map", WireMock.equalTo("{\"dataSource\":\"test-data-source\"," +
                        "\"did\":\"" + did + "\",\"filename\":\"test-content\",\"flow\":\"test-flow-name\"," +
                        "\"key-1\":\"value-1\",\"key-2\":\"value-2\",\"originalFilename\":\"test-delta-file\"}"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .willReturn(WireMock.ok()));

        HttpEgress.Parameters params = makeParameters(HttpRequestMethod.POST);
        EgressResultType egressResultType = runTest(did, params);
        assertInstanceOf(EgressResult.class, egressResultType);
    }

    @Test
    void egressPostWithExtraHeaders() {
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo("text/plain")) // TODO - should extra parameters be allowed to override this?
                .withHeader("extraKey", WireMock.equalTo("extraValue"))
                .withHeader(HttpHeaders.CONTENT_LENGTH, WireMock.matching("" + CONTENT.getBytes().length))
                .withHeader("headers-map", WireMock.equalTo("{\"dataSource\":\"test-data-source\"," +
                        "\"did\":\"" + did + "\",\"filename\":\"test-content\",\"flow\":\"test-flow-name\"," +
                        "\"key-1\":\"value-1\",\"key-2\":\"value-2\",\"originalFilename\":\"test-delta-file\"}"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .willReturn(WireMock.ok()));

        HttpEgress.Parameters params = makeParameters(HttpRequestMethod.POST,
                Map.of("extraKey", "extraValue", "content-type", "xml"));
        EgressResultType egressResultType = runTest(did, params);
        assertInstanceOf(EgressResult.class, egressResultType);
    }

    @Test
    void egressPut() {
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.put(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader(HttpHeaders.CONTENT_LENGTH, WireMock.matching("" + CONTENT.getBytes().length))
                .withHeader("headers-map", WireMock.equalTo("{\"dataSource\":\"test-data-source\"," +
                        "\"did\":\"" + did + "\",\"filename\":\"test-content\",\"flow\":\"test-flow-name\"," +
                        "\"key-1\":\"value-1\",\"key-2\":\"value-2\",\"originalFilename\":\"test-delta-file\"}"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .willReturn(WireMock.ok()));

        HttpEgress.Parameters params = makeParameters(HttpRequestMethod.PUT);
        EgressResultType egressResultType = runTest(did, params);
        assertInstanceOf(EgressResult.class, egressResultType);
    }

    @Test
    void egressPatch() {
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.patch(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader(HttpHeaders.CONTENT_LENGTH, WireMock.matching("" + CONTENT.getBytes().length))
                .withHeader("headers-map", WireMock.equalTo("{\"dataSource\":\"test-data-source\"," +
                        "\"did\":\"" + did + "\",\"filename\":\"test-content\",\"flow\":\"test-flow-name\"," +
                        "\"key-1\":\"value-1\",\"key-2\":\"value-2\",\"originalFilename\":\"test-delta-file\"}"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .willReturn(WireMock.ok()));

        HttpEgress.Parameters params = makeParameters(HttpRequestMethod.PATCH);
        EgressResultType egressResultType = runTest(did, params);
        assertInstanceOf(EgressResult.class, egressResultType);
    }

    private EgressResultType runTest(UUID did, HttpEgress.Parameters params) {
        return action.egress(
                ActionContext.builder()
                        .contentStorageService(CONTENT_STORAGE_SERVICE)
                        .did(did)
                        .deltaFileName("test-delta-file")
                        .dataSource("test-data-source")
                        .flowName("test-flow-name")
                        .build(),
                params, egressInput());
    }

    private HttpEgress.Parameters makeParameters(HttpRequestMethod method) {
        return makeParameters(method, null);
    }

    private HttpEgress.Parameters makeParameters(HttpRequestMethod method, Map<String, String> extraHeaders) {
        HttpEgress.Parameters params = new HttpEgress.Parameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        params.setUrl(url);
        params.setMetadataKey("headers-map");
        params.setMethod(method);
        if (extraHeaders != null) {
            params.setExtraHeaders(extraHeaders);
        }
        return params;
    }

    private EgressInput egressInput() {
        return EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();
    }
}
