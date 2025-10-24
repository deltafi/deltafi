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
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.UUID;

class RestPostEgressTest {
    private static final String CONTENT = "This is the test content.";

    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("RestPostEgressTest");
    RestPostEgress restPostEgress = new RestPostEgress(Mockito.mock(OkHttpClient.class));

    @Test
    @SneakyThrows
    void buildHeaders() {
        EgressInput egressInput = EgressInput.builder()
                .content(runner.saveContent(CONTENT, "test-content", MediaType.TEXT_PLAIN))
                .metadata(Map.of("key-1", "value-1", "key-2", "value-2"))
                .build();

        ActionContext actionContext = runner.actionContextBuilder()
                .did(new UUID(0, 0))
                .deltaFileName("test-delta-file")
                .dataSource("test-data-source")
                .flowName("test-flow-name")
                .build();

        RestPostEgressParameters restPostEgressParameters = new RestPostEgressParameters();
        restPostEgressParameters.setMetadataKey("headers-map");

        Map<String, String> headers = restPostEgress.buildHeaders(actionContext, restPostEgressParameters, egressInput);

        Assertions.assertThat(headers).containsEntry("headers-map", "{\"dataSource\":\"test-data-source\"," +
                "\"did\":\"00000000-0000-0000-0000-000000000000\",\"filename\":\"test-content\",\"flow\":\"test-flow-name\"," +
                "\"key-1\":\"value-1\",\"key-2\":\"value-2\",\"originalFilename\":\"test-delta-file\"}");
    }
}
