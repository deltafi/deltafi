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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.UUID;

class DeltaFiEgressTest {
    private static final String CONTENT = "This is the test content.";
    private static final UUID DID = new UUID(0, 0);
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("DeltaFiEgressTest");

    private final OkHttpClient okHttpClient = Mockito.mock(OkHttpClient.class);
    private final DeltaFiEgress action = new DeltaFiEgress(okHttpClient, "https:/deltafi-core");

    @Test
    @SneakyThrows
    void egressToDefaultLocal() {
        DeltaFiEgressParameters deltaFiEgressParameters = new DeltaFiEgressParameters();
        deltaFiEgressParameters.setSendLocal(true);

        action.egress(getContext(), deltaFiEgressParameters, getEgressInput());
        Mockito.verify(okHttpClient).newCall(Mockito.assertArg(this::verifyRequest));
    }

    @SneakyThrows
    void verifyRequest(Request request) {
        Assertions.assertThat(request.url()).hasToString("https://deltafi-core/api/v2/deltafile/ingress");
        Assertions.assertThat(request.method()).isEqualTo("POST");
        Assertions.assertThat(request.body()).isNotNull();
        // this is the value used to set the content-length header (added by OKHttp during the request execution)
        Assertions.assertThat(request.body().contentLength()).isEqualTo(CONTENT.length());
        // this is the value used to set the content-type header (added by OKHttp during the request execution)
        Assertions.assertThat(request.body().contentType()).hasToString("text/plain");
    }

    @Test
    void errorInvalidUrl() {
        ResultType resultType = action.egress(getContext(), new DeltaFiEgressParameters(), getEgressInput());

        ErrorResultAssert.assertThat(resultType)
                .hasCause("URL cannot be determined");
    }

    @Test
    void errorCircularEgress() {
        DeltaFiEgressParameters deltaFiEgressParameters = new DeltaFiEgressParameters();
        deltaFiEgressParameters.setSendLocal(true);
        deltaFiEgressParameters.setFlow("data-source");

        ResultType resultType = action.egress(getContext(), deltaFiEgressParameters, getEgressInput());

        ErrorResultAssert.assertThat(resultType)
                .hasCause("Circular egress detected");
    }

    @Test
    @SneakyThrows
    void buildHeadersForRemote() {
        DeltaFiEgressParameters deltaFiEgressParameters = new DeltaFiEgressParameters();
        deltaFiEgressParameters.setUrl("https://remote-deltafi");
        deltaFiEgressParameters.setFlow("test-flow-name");

        Map<String, String> headers = action.buildHeaders(getContext(), deltaFiEgressParameters, getEgressInput());

        Map<String, String> expectedHeaders = Map.of(
                "Filename", "test-content",
                "DataSource","test-flow-name",
                "Metadata", "{\"key-1\":\"value-1\",\"key-2\":\"value-2\"," +
                        "\"originalDid\":\"00000000-0000-0000-0000-000000000000\",\"originalSystem\":\"test-system-name\"}");

        Assertions.assertThat(headers).containsAllEntriesOf(expectedHeaders);
    }

    @Test
    @SneakyThrows
    void buildHeadersForLocal() {
        DeltaFiEgressParameters deltaFiEgressParameters = new DeltaFiEgressParameters();
        deltaFiEgressParameters.setSendLocal(true);

        Map<String, String> headers = action.buildHeaders(getContext(), deltaFiEgressParameters, getEgressInput());

        Map<String, String> expectedHeaders = Map.of(
                "Filename", "test-content",
                "X-User-Name", "host",
                "X-User-Permissions", "DeltaFileIngress",
                "Metadata", "{\"key-1\":\"value-1\",\"key-2\":\"value-2\"," +
                        "\"originalDid\":\"00000000-0000-0000-0000-000000000000\",\"originalSystem\":\"test-system-name\"}");

        Assertions.assertThat(headers).containsAllEntriesOf(expectedHeaders);
    }

    @Test
    @SneakyThrows
    void buildHeadersNoContent() {
        Map<String, String> headers = action.buildHeaders(getContext(), new DeltaFiEgressParameters(), EgressInput.builder().build());
        Assertions.assertThat(headers).containsEntry("Filename", "");
    }

    private EgressInput getEgressInput() {
        return EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();
    }

    ActionContext getContext() {
        return runner.actionContextBuilder()
                .dataSource("data-source")
                .did(DID)
                .systemName("test-system-name")
                .hostname("host")
                .build();
    }

}
