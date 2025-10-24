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
import okhttp3.*;
import okio.Buffer;
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.asserters.FilterResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

    private static final String URL = "https://somewhere/endpoint";
    private static final String CONTENT = "This is the test content.";

    private final OkHttpClient okHttpClient = Mockito.mock(OkHttpClient.class);
    private final TestHttpEgress action = new TestHttpEgress(okHttpClient);
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("RestPostEgressTest");

    @Test
    void egresses() {
        Response mockResponse = getMockResponse();
        Mockito.when(mockResponse.isSuccessful()).thenReturn(true);
        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        params.setUrl(URL);
        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(EgressResult.class, egressResultType);
        Mockito.verify(okHttpClient).newCall(Mockito.assertArg(this::verifyRequest));
    }

    @SneakyThrows
    void verifyRequest(Request request) {
        Assertions.assertThat(request.url()).hasToString(URL);
        Assertions.assertThat(request.method()).isEqualTo("POST");
        Assertions.assertThat(request.headers()).hasSize(2);
        Assertions.assertThat(request.header("header-1")).isEqualTo("value-1");
        Assertions.assertThat(request.header("header-2")).isEqualTo("value-2");
        Assertions.assertThat(request.body()).isNotNull();
        // this is the value used to set the content-length header (added by OKHttp during the request execution)
        Assertions.assertThat(request.body().contentLength()).isEqualTo(CONTENT.length());
        // this is the value used to set the content-type header (added by OKHttp during the request execution)
        Assertions.assertThat(request.body().contentType()).hasToString("text/plain");
    }

    @Test
    @SneakyThrows
    void errors() {
        Response mockResponse = getMockResponse();
        Mockito.when(mockResponse.isSuccessful()).thenReturn(false);
        Mockito.when(mockResponse.code()).thenReturn(500);
        ResponseBody responseBody = Mockito.mock(ResponseBody.class);
        Mockito.when(mockResponse.body()).thenReturn(responseBody);
        Mockito.when(responseBody.string()).thenReturn("There was an error!");

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        params.setUrl(URL);
        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(ErrorResult.class, egressResultType);
        assertEquals("Unsuccessful HTTP POST: 500", ((ErrorResult) egressResultType).getErrorCause());
        assertEquals("There was an error!", ((ErrorResult) egressResultType).getErrorContext());
    }

    @Test
    void errorsOnHttpPostException() {
        Mockito.when(okHttpClient.newCall(Mockito.any()))
                .thenThrow(new RuntimeException());

        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        params.setUrl(URL);
        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(ErrorResult.class, egressResultType);
        assertEquals("Unexpected error during POST request", ((ErrorResult) egressResultType).getErrorCause());
    }

    @Test
    @SneakyThrows
    void errorsOnIoExceptionOpeningInputStream() {
        Call mockCall = Mockito.mock(Call.class);
        Mockito.when(okHttpClient.newCall(Mockito.any())).thenReturn(mockCall);
        Mockito.when(mockCall.execute()).thenThrow(new IOException());
        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        params.setUrl(URL);
        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        assertInstanceOf(ErrorResult.class, egressResultType);
        assertEquals("Service POST failure", ((ErrorResult) egressResultType).getErrorCause());
    }

    @Test
    void handlesNullContentWithDefaultPolicy() {
        EgressInput egressInput = EgressInput.builder()
                .content(null)  // null content
                .metadata(Map.of("test", "value"))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        params.setUrl(URL);
        // Default policy is ERROR

        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        // Should return ErrorResult with default ERROR policy
        ErrorResultAssert.assertThat(egressResultType)
                        .hasCause("Cannot perform egress: no content available");
    }

    @Test
    void handlesNullContentWithFilterPolicy() {
        EgressInput egressInput = EgressInput.builder()
                .content(null)  // null content
                .metadata(Map.of("test", "value"))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        params.setUrl(URL);
        params.setNoContentPolicy(NoContentPolicy.FILTER);

        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        // Should return FilterResult
        FilterResultAssert.assertThat(egressResultType)
                .hasCause("Content is null - filtered by noContentPolicy");
    }

    @Test
    void handlesNullContentWithErrorPolicy() {
        EgressInput egressInput = EgressInput.builder()
                .content(null)  // null content
                .metadata(Map.of("test", "value"))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        params.setUrl(URL);
        params.setNoContentPolicy(NoContentPolicy.ERROR);

        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        // Should return ErrorResult
        ErrorResultAssert.assertThat(egressResultType)
                .hasCause("Cannot perform egress: no content available");
    }

    @Test
    void handlesNullContentWithSendEmptyPolicy() {
        Response mockResponse = getMockResponse();
        Mockito.when(mockResponse.isSuccessful()).thenReturn(true);
        EgressInput egressInput = EgressInput.builder()
                .content(null)  // null content
                .metadata(Map.of("test", "value"))
                .build();

        HttpEgressParameters params = new HttpEgressParameters();
        params.setUrl(URL);
        params.setNoContentPolicy(NoContentPolicy.SEND_EMPTY);

        EgressResultType egressResultType = action.egress(runner.actionContext(), params, egressInput);

        // Should succeed with zero data sent
        assertInstanceOf(EgressResult.class, egressResultType);
        Mockito.verify(okHttpClient).newCall(Mockito.assertArg(this::verifyEmptyRequest));
    }

    @SneakyThrows
    void verifyEmptyRequest(Request request) {
        Assertions.assertThat(request.url()).hasToString(URL);
        Assertions.assertThat(request.method()).isEqualTo("POST");
        Assertions.assertThat(request.headers()).hasSize(2);
        Assertions.assertThat(request.header("header-1")).isEqualTo("value-1");
        Assertions.assertThat(request.header("header-2")).isEqualTo("value-2");
        Assertions.assertThat(request.body()).isNotNull();
        // this is the value used to set the content-length header (added by OKHttp during the request execution)
        Assertions.assertThat(request.body().contentLength()).isZero();
        // when the body is null the IngressStreamRequestBody should return null for the contentType
        Assertions.assertThat(request.body().contentType()).isNull();
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        Assertions.assertThat(buffer.readUtf8()).isEmpty();
    }

    @SneakyThrows
    Response getMockResponse() {
        Call mockCall = Mockito.mock(Call.class);
        Response mockResponse = Mockito.mock(Response.class);
        Mockito.when(okHttpClient.newCall(Mockito.any())).thenReturn(mockCall);
        Mockito.when(mockCall.execute()).thenReturn(mockResponse);
        return mockResponse;
    }
}
