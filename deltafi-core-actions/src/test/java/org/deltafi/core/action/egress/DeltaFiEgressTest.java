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
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.common.content.ActionContentStorageService;
import org.deltafi.common.test.content.InMemoryContentStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DeltaFiEgressTest {
    private static final String URL_ENDPOINT = "/endpoint";
    private static final String ENV_CORE_URL = null;
    private static final String CONTENT = "This is the test content.";
    private static final ActionContentStorageService CONTENT_STORAGE_SERVICE =
            new InMemoryContentStorageService();

    @RegisterExtension
    static WireMockExtension wireMockHttp = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort().http2PlainDisabled(true))
            .build();
    private final OkHttpClient httpService = new OkHttpClient();
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("DeltaFiEgressTest");

    private final DeltaFiEgress action = new DeltaFiEgress(httpService, ENV_CORE_URL);

    @BeforeEach
    void beforeEach() {
        wireMockHttp.resetAll();
    }

    @Test
    void errorInvalidUrl() {
        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        DeltaFiEgressParameters deltaFiEgressParameters = new DeltaFiEgressParameters();
        ResultType resultType = action.egress(
                getContext(UUID.randomUUID()), deltaFiEgressParameters, egressInput);

        ErrorResultAssert.assertThat(resultType)
                .hasCause("URL cannot be determined");
    }

    @Test
    void errorCircularEgress() {
        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        DeltaFiEgressParameters deltaFiEgressParameters = new DeltaFiEgressParameters();
        deltaFiEgressParameters.setSendLocal(true);
        deltaFiEgressParameters.setFlow("data-source");

        ResultType resultType = action.egress(
                getContext(UUID.randomUUID()), deltaFiEgressParameters, egressInput);

        ErrorResultAssert.assertThat(resultType)
                .hasCause("Circular egress detected");
    }

    @Test
    void egressRemote() {
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.post(URL_ENDPOINT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader(HttpHeaders.CONTENT_LENGTH, WireMock.equalTo(String.valueOf(CONTENT.length())))
                .withHeader("Filename", WireMock.equalTo("test-content"))
                .withHeader("DataSource", WireMock.equalTo("test-flow-name"))
                .withHeader("Metadata", WireMock.equalTo("{\"key-1\":\"value-1\",\"key-2\":\"value-2\"," +
                        "\"originalDid\":\"" + did + "\",\"originalSystem\":\"test-system-name\"}"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .willReturn(WireMock.ok()));

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        DeltaFiEgressParameters deltaFiEgressParameters = new DeltaFiEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_ENDPOINT;
        deltaFiEgressParameters.setUrl(url);
        deltaFiEgressParameters.setFlow("test-flow-name");
        EgressResultType egressResultType = action.egress(
                getContext(did), deltaFiEgressParameters, egressInput);

        assertInstanceOf(EgressResult.class, egressResultType);
    }

    @Test
    void egressEmptyContent() {
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.post(URL_ENDPOINT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader(HttpHeaders.CONTENT_LENGTH, WireMock.equalTo("0"))
                .withHeader("Filename", WireMock.equalTo("test-content"))
                .withHeader("DataSource", WireMock.equalTo("test-flow-name"))
                .withHeader("Metadata", WireMock.equalTo("{\"key-1\":\"value-1\",\"key-2\":\"value-2\"," +
                        "\"originalDid\":\"" + did + "\",\"originalSystem\":\"test-system-name\"}"))
                .withRequestBody(WireMock.absent())
                .willReturn(WireMock.ok()));

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent("", "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        DeltaFiEgressParameters deltaFiEgressParameters = new DeltaFiEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_ENDPOINT;
        deltaFiEgressParameters.setUrl(url);
        deltaFiEgressParameters.setFlow("test-flow-name");
        EgressResultType egressResultType = action.egress(
                getContext(did), deltaFiEgressParameters, egressInput);

        assertInstanceOf(EgressResult.class, egressResultType);
    }

    @Test
    void egressLocalWithUrl() {
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.post(URL_ENDPOINT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader(HttpHeaders.CONTENT_LENGTH, WireMock.equalTo(String.valueOf(CONTENT.length())))
                .withHeader("Filename", WireMock.equalTo("test-content"))
                .withHeader("DataSource", WireMock.equalTo("test-flow-name"))
                .withHeader("X-User-Name", WireMock.equalTo("host"))
                .withHeader("X-User-Permissions", WireMock.equalTo("DeltaFileIngress"))
                .withHeader("Metadata", WireMock.equalTo("{\"key-1\":\"value-1\",\"key-2\":\"value-2\"," +
                        "\"originalDid\":\"" + did + "\",\"originalSystem\":\"test-system-name\"}"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .willReturn(WireMock.ok()));

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        DeltaFiEgressParameters deltaFiEgressParameters = new DeltaFiEgressParameters();
        deltaFiEgressParameters.setUrl(wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_ENDPOINT);
        deltaFiEgressParameters.setSendLocal(true);
        deltaFiEgressParameters.setFlow("test-flow-name");
        EgressResultType egressResultType = action.egress(
                getContext(did), deltaFiEgressParameters, egressInput);

        assertInstanceOf(EgressResult.class, egressResultType);
    }

    @Test
    void egressLocalDefaultUrl() {
        String mockHttpUrl = wireMockHttp.getRuntimeInfo().getHttpBaseUrl();
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.post(DeltaFiEgress.INGRESS_URL_PATH)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader(HttpHeaders.CONTENT_LENGTH, WireMock.equalTo(String.valueOf(CONTENT.length())))
                .withHeader("Filename", WireMock.equalTo("test-content"))
                .withHeader("DataSource", WireMock.equalTo("test-flow-name"))
                .withHeader("X-User-Name", WireMock.equalTo("host"))
                .withHeader("X-User-Permissions", WireMock.equalTo("DeltaFileIngress"))
                .withHeader("Metadata", WireMock.equalTo("{\"key-1\":\"value-1\",\"key-2\":\"value-2\"," +
                        "\"originalDid\":\"" + did + "\",\"originalSystem\":\"test-system-name\"}"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .willReturn(WireMock.ok()));

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        DeltaFiEgress localAction = new DeltaFiEgress(httpService, mockHttpUrl);

        DeltaFiEgressParameters deltaFiEgressParameters = new DeltaFiEgressParameters();
        deltaFiEgressParameters.setSendLocal(true);
        deltaFiEgressParameters.setFlow("test-flow-name");
        EgressResultType egressResultType = localAction.egress(
                getContext(did), deltaFiEgressParameters, egressInput);

        assertInstanceOf(EgressResult.class, egressResultType);
    }

    ActionContext getContext(UUID did) {
        return ActionContext.builder()
                .contentStorageService(CONTENT_STORAGE_SERVICE)
                .dataSource("data-source")
                .did(did)
                .systemName("test-system-name")
                .hostname("host")
                .build();
    }

}
