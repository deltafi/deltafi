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
package org.deltafi.core.action.egress;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;

import static org.deltafi.common.constant.DeltaFiConstants.BYTES_OUT;
import static org.deltafi.common.constant.DeltaFiConstants.FILES_OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DeltaFiEgressTest {
    private static final String URL_CONTEXT = "/endpoint";
    private static final String CONTENT = "This is the test content.";

    private final HttpService httpService = new HttpService(HttpClient.newHttpClient());
    private final DeltaFiEgress action = new DeltaFiEgress(httpService);
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("DeltaFiEgressTest");

    private static final ContentStorageService CONTENT_STORAGE_SERVICE =
            new ContentStorageService(new InMemoryObjectStorageService());

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
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.TEXT_PLAIN))
                .withHeader("filename", WireMock.equalTo("test-content"))
                .withHeader("flow", WireMock.equalTo("test-flow-name"))
                .withHeader("metadata", WireMock.equalTo("{\"key-1\":\"value-1\",\"key-2\":\"value-2\"," +
                        "\"originalDid\":\"" + did + "\",\"originalSystem\":\"test-system-name\"}"))
                .withRequestBody(WireMock.equalTo(CONTENT))
                .willReturn(WireMock.ok()));

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        DeltaFiEgressParameters deltaFiEgressParameters = new DeltaFiEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        deltaFiEgressParameters.setUrl(url);
        deltaFiEgressParameters.setFlow("test-flow-name");
        EgressResultType egressResultType = action.egress(
                ActionContext.builder()
                        .contentStorageService(CONTENT_STORAGE_SERVICE)
                        .did(did)
                        .systemName("test-system-name")
                        .build(),
                deltaFiEgressParameters, egressInput);

        assertInstanceOf(EgressResult.class, egressResultType);
        assertEquals(FILES_OUT, ((EgressResult) egressResultType).getMetrics().getFirst().getName());
        assertEquals(1, ((EgressResult) egressResultType).getMetrics().getFirst().getValue());
        assertEquals(url, ((EgressResult) egressResultType).getMetrics().getFirst().getTags().get("endpoint"));
        assertEquals(BYTES_OUT, ((EgressResult) egressResultType).getMetrics().getLast().getName());
        assertEquals(CONTENT.length(), ((EgressResult) egressResultType).getMetrics().getLast().getValue());
        assertEquals(url, ((EgressResult) egressResultType).getMetrics().getLast().getTags().get("endpoint"));
    }
}
