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
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import okhttp3.OkHttpClient;
import org.apache.nifi.util.FlowFileUnpackagerV1;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.common.content.ActionContentStorageService;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.deltafi.common.nifi.ContentType.APPLICATION_FLOWFILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FlowfileEgressTest {
    private static final String URL_CONTEXT = "/endpoint";
    private static final String CONTENT = "This is the test content.";

    private final FlowfileEgress action = new FlowfileEgress(new OkHttpClient());
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("FlowfileEgressTest");

    private static final ActionContentStorageService CONTENT_STORAGE_SERVICE =
            new ActionContentStorageService(new InMemoryObjectStorageService());

    @RegisterExtension
    static WireMockExtension wireMockHttp = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort().http2PlainDisabled(true))
            .build();

    @BeforeEach
    public void beforeEach() {
        wireMockHttp.resetAll();
    }

    @Test
    void egresses() throws IOException {
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(APPLICATION_FLOWFILE))
                .willReturn(WireMock.ok()));

        HttpEgressParameters httpEgressParameters = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        httpEgressParameters.setUrl(url);

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        EgressResultType egressResultType = action.egress(
                ActionContext.builder()
                        .contentStorageService(CONTENT_STORAGE_SERVICE)
                        .did(did)
                        .deltaFileName("test-delta-file")
                        .dataSource("test-data-source")
                        .flowName("test-flow-name")
                        .build(),
                httpEgressParameters, egressInput);

        assertInstanceOf(EgressResult.class, egressResultType);

        List<ServeEvent> serveEventList = wireMockHttp.getServeEvents().getRequests();
        byte[] flowfileSent = serveEventList.getFirst().getRequest().getBody();
        FlowFileUnpackagerV1 flowFileUnpackagerV1 = new FlowFileUnpackagerV1();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Map<String, String> flowfileAttributes = flowFileUnpackagerV1.unpackageFlowFile(
                new ByteArrayInputStream(flowfileSent), byteArrayOutputStream);
        assertEquals(CONTENT, byteArrayOutputStream.toString());
        assertEquals("value-1", flowfileAttributes.get("key-1"));
        assertEquals("value-2", flowfileAttributes.get("key-2"));
        assertEquals(did.toString(), flowfileAttributes.get("did"));
        assertEquals("test-data-source", flowfileAttributes.get("dataSource"));
        assertEquals("test-flow-name", flowfileAttributes.get("flow"));
        assertEquals("test-delta-file", flowfileAttributes.get("originalFilename"));
        assertEquals("test-content", flowfileAttributes.get("filename"));
    }

    @Test
    void handlesNullContentGracefully() throws IOException {
        UUID did = UUID.randomUUID();

        wireMockHttp.stubFor(WireMock.post(URL_CONTEXT)
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(APPLICATION_FLOWFILE))
                .willReturn(WireMock.ok()));

        HttpEgressParameters httpEgressParameters = new HttpEgressParameters();
        String url = wireMockHttp.getRuntimeInfo().getHttpBaseUrl() + URL_CONTEXT;
        httpEgressParameters.setUrl(url);
        httpEgressParameters.setNoContentPolicy(NoContentPolicy.SEND_EMPTY);  // Explicitly set to send empty

        EgressInput egressInput = EgressInput.builder()
                .content(null)  // null content
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        EgressResultType egressResultType = action.egress(
                ActionContext.builder()
                        .contentStorageService(CONTENT_STORAGE_SERVICE)
                        .did(did)
                        .deltaFileName("test-delta-file")
                        .dataSource("test-data-source")
                        .flowName("test-flow-name")
                        .build(),
                httpEgressParameters, egressInput);

        // Should succeed with zero data sent
        assertInstanceOf(EgressResult.class, egressResultType);

        List<ServeEvent> serveEventList = wireMockHttp.getServeEvents().getRequests();
        byte[] flowfileSent = serveEventList.getFirst().getRequest().getBody();
        FlowFileUnpackagerV1 flowFileUnpackagerV1 = new FlowFileUnpackagerV1();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Map<String, String> flowfileAttributes = flowFileUnpackagerV1.unpackageFlowFile(
                new ByteArrayInputStream(flowfileSent), byteArrayOutputStream);
        
        // Content should be empty (zero bytes)
        assertEquals("", byteArrayOutputStream.toString());
        
        // Metadata should still be present
        assertEquals("value-1", flowfileAttributes.get("key-1"));
        assertEquals("value-2", flowfileAttributes.get("key-2"));
        assertEquals(did.toString(), flowfileAttributes.get("did"));
        assertEquals("test-data-source", flowfileAttributes.get("dataSource"));
        assertEquals("test-flow-name", flowfileAttributes.get("flow"));
        assertEquals("test-delta-file", flowfileAttributes.get("originalFilename"));
        assertEquals("", flowfileAttributes.get("filename"));  // Should use empty string for null content
    }
}
