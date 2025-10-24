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
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HttpEgressTest {
    private static final UUID DID = new UUID(0, 0);
    private static final String URL = "https://somewhere/endpoint";
    private static final String CONTENT = "This is the test content.";
    private static final String METADATA_JSON = "{\"dataSource\":\"test-data-source\"," +
            "\"did\":\"00000000-0000-0000-0000-000000000000\",\"filename\":\"test-content\",\"flow\":\"test-flow-name\"," +
            "\"key-1\":\"value-1\",\"key-2\":\"value-2\",\"originalFilename\":\"test-delta-file\"}";

    private final OkHttpClient okHttpClient = Mockito.mock(OkHttpClient.class);
    private final HttpEgress action = new HttpEgress(okHttpClient);
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("HttpEgressTest");

    @BeforeEach
    void mockOkHttpClient() throws IOException {
        Call mockCall = Mockito.mock(Call.class);
        Response mockResponse = Mockito.mock(Response.class);
        Mockito.when(okHttpClient.newCall(Mockito.any())).thenReturn(mockCall);
        Mockito.when(mockCall.execute()).thenReturn(mockResponse);
        Mockito.when(mockResponse.isSuccessful()).thenReturn(true);
    }

    @Test
    void egressDelete() {
        EgressResultType egressResultType = runTest(HttpRequestMethod.DELETE);
        assertInstanceOf(EgressResult.class, egressResultType);
        Mockito.verify(okHttpClient).newCall(Mockito.assertArg(this::verifyDelete));
    }

    private void verifyDelete(Request request) {
        Assertions.assertThat(request.method()).isEqualTo("DELETE");
        Assertions.assertThat(request.url()).hasToString(URL);
        Assertions.assertThat(request.headers()).hasSize(1);
        Assertions.assertThat(request.headers().get("headers-map")).isEqualTo(METADATA_JSON);
    }

    @Test
    void egressPost() {
        EgressResultType egressResultType = runTest(HttpRequestMethod.POST);
        assertInstanceOf(EgressResult.class, egressResultType);
        Mockito.verify(okHttpClient).newCall(Mockito.assertArg(request -> verifyWithBody(request, "POST")));
    }

    @Test
    void egressPostWithExtraHeaders() {
        EgressResultType egressResultType = runTest(HttpRequestMethod.POST,
                Map.of("extraKey", "extraValue", "content-type", "xml"));
        assertInstanceOf(EgressResult.class, egressResultType);
        Mockito.verify(okHttpClient).newCall(Mockito.assertArg(this::verifyPostWithExtraHeaders));
    }

    @SneakyThrows
    private void verifyPostWithExtraHeaders(Request request) {
        Assertions.assertThat(request.method()).isEqualTo("POST");
        Assertions.assertThat(request.url()).hasToString(URL);
        Assertions.assertThat(request.headers()).hasSize(3);
        Assertions.assertThat(request.headers().get("headers-map")).isEqualTo(METADATA_JSON);
        Assertions.assertThat(request.headers().get("extraKey")).isEqualTo("extraValue");
        // this is replaced by the client with the body.contentType(), setting it has no impact on the final request
        Assertions.assertThat(request.headers().get("content-type")).isEqualTo("xml");
        Assertions.assertThat(request.body()).isNotNull();

        Assertions.assertThat(request.body().contentLength()).isEqualTo(CONTENT.length());
        Assertions.assertThat(request.body().contentType()).hasToString("text/plain");
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        Assertions.assertThat(buffer.readUtf8()).isEqualTo(CONTENT);
    }

    @Test
    void egressPut() {
        EgressResultType egressResultType = runTest(HttpRequestMethod.PUT);
        assertInstanceOf(EgressResult.class, egressResultType);
        Mockito.verify(okHttpClient).newCall(Mockito.assertArg(request -> verifyWithBody(request, "PUT")));
    }

    @Test
    void egressPatch() {
        EgressResultType egressResultType = runTest(HttpRequestMethod.PATCH);
        assertInstanceOf(EgressResult.class, egressResultType);
        Mockito.verify(okHttpClient).newCall(Mockito.assertArg(request -> verifyWithBody(request, "PATCH")));
    }

    @SneakyThrows
    private void verifyWithBody(Request request, String method) {
        Assertions.assertThat(request.method()).isEqualTo(method);
        Assertions.assertThat(request.url()).hasToString(URL);
        Assertions.assertThat(request.headers()).hasSize(1);
        Assertions.assertThat(request.headers().get("headers-map")).isEqualTo(METADATA_JSON);
        Assertions.assertThat(request.body()).isNotNull();

        Assertions.assertThat(request.body().contentLength()).isEqualTo(CONTENT.length());
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        Assertions.assertThat(buffer.readUtf8()).isEqualTo(CONTENT);
    }

    private EgressResultType runTest(HttpRequestMethod method) {
        return runTest(method, null);
    }

    private EgressResultType runTest(HttpRequestMethod method, Map<String, String> extraHeaders) {
        return action.egress(
                runner.actionContextBuilder()
                        .did(DID)
                        .deltaFileName("test-delta-file")
                        .dataSource("test-data-source")
                        .flowName("test-flow-name")
                        .build(),
                makeParameters(method, extraHeaders), egressInput());
    }

    private HttpEgress.Parameters makeParameters(HttpRequestMethod method, Map<String, String> extraHeaders) {
        HttpEgress.Parameters params = new HttpEgress.Parameters();
        params.setUrl(URL);
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
