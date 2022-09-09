/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.services.api;

import org.deltafi.core.domain.services.api.model.DiskMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeltafiApiRestClientTest {
    DeltafiApiRestClient deltafiApiRestClient = new DeltafiApiRestClient("https://url.com");

    @Mock
    HttpClient httpClient;

    @Test
    void testContentMetrics() throws IOException, InterruptedException {
        ReflectionTestUtils.setField(deltafiApiRestClient, "httpClient", httpClient);
        HttpResponse<Object> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"content\":{\"limit\":107362627584,\"usage\":28682784768},\"timestamp\":\"2022-05-17 14:37:28 -0400\"}");
        when(httpClient.send(any(), any())).thenReturn(response);
        DiskMetrics diskMetrics = deltafiApiRestClient.contentMetrics();
        assertEquals(107362627584L, diskMetrics.getLimit());
        assertEquals(28682784768L, diskMetrics.getUsage());
        assertEquals(26.72, (double) Math.round(diskMetrics.percentUsed() * 100) / 100);
    }
}
