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

import lombok.SneakyThrows;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import org.apache.nifi.util.FlowFileUnpackagerV1;
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FlowfileEgressTest {
    private static final String CONTENT = "This is the test content.";
    private static final UUID DID = new UUID(0, 0);
    private static final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("FlowfileEgressTest");
    private static final ActionContext ACTION_CONTEXT = runner.actionContextBuilder()
            .did(DID)
            .deltaFileName("test-delta-file")
            .dataSource("test-data-source")
            .flowName("test-flow-name")
            .build();

    private final OkHttpClient okHttpClient = Mockito.mock(OkHttpClient.class);
    private final FlowfileEgress action = new FlowfileEgress(okHttpClient);

    @BeforeEach
    void mockOkHttpClient() throws IOException {
        Call mockCall = Mockito.mock(Call.class);
        Response mockResponse = Mockito.mock(Response.class);
        Mockito.when(okHttpClient.newCall(Mockito.any())).thenReturn(mockCall);
        Mockito.when(mockCall.execute()).thenReturn(mockResponse);
        Mockito.when(mockResponse.isSuccessful()).thenReturn(true);
    }

    @Test
    void egresses() {
        HttpEgressParameters httpEgressParameters = new HttpEgressParameters();
        httpEgressParameters.setUrl("https://somewhere/api");

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        EgressResultType egressResultType = action.egress(ACTION_CONTEXT, httpEgressParameters, egressInput);

        assertInstanceOf(EgressResult.class, egressResultType);

        Mockito.verify(okHttpClient).newCall(Mockito.assertArg(this::verifyPopulatedFlowFileBody));
    }

    private void verifyPopulatedFlowFileBody(Request request) {
        verifyFlowFile(request, "test-content", CONTENT);
    }

    @Test
    void handlesNullContentGracefully() {
        HttpEgressParameters httpEgressParameters = new HttpEgressParameters();
        httpEgressParameters.setUrl("https://somewhere/api");
        httpEgressParameters.setNoContentPolicy(NoContentPolicy.SEND_EMPTY);  // Explicitly set to send empty

        EgressInput egressInput = EgressInput.builder()
                .content(null)
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        EgressResultType egressResultType = action.egress(ACTION_CONTEXT, httpEgressParameters, egressInput);

        // Should succeed with zero data sent
        assertInstanceOf(EgressResult.class, egressResultType);

        Mockito.verify(okHttpClient).newCall(Mockito.assertArg(this::verifyEmptyFlowFile));
    }

    private void verifyEmptyFlowFile(Request request) {
        // Content should be empty (zero bytes) and the filename should use empty string for null content
        verifyFlowFile(request, "", "");
    }

    @SneakyThrows
    private void verifyFlowFile(Request request, String expectedFilename, String expectedBody) {
        Assertions.assertThat(request.body()).isNotNull();
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        FlowFileUnpackagerV1 flowFileUnpackagerV1 = new FlowFileUnpackagerV1();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Map<String, String> flowfileAttributes = flowFileUnpackagerV1.unpackageFlowFile(buffer.inputStream(), byteArrayOutputStream);

        assertEquals(expectedBody, byteArrayOutputStream.toString());

        assertEquals("value-1", flowfileAttributes.get("key-1"));
        assertEquals("value-2", flowfileAttributes.get("key-2"));
        assertEquals("00000000-0000-0000-0000-000000000000", flowfileAttributes.get("did"));
        assertEquals("test-data-source", flowfileAttributes.get("dataSource"));
        assertEquals("test-flow-name", flowfileAttributes.get("flow"));
        assertEquals("test-delta-file", flowfileAttributes.get("originalFilename"));
        assertEquals(expectedFilename, flowfileAttributes.get("filename"));
    }
}
