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
package org.deltafi.common.http.client.feign;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FeignClientFactoryTest {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TestClass {
        private String a;
        private String b;
        private int c;
        private double d;
    }

    private interface TestClient {
        @RequestLine("GET /endpoint?param1={param1}&param2={param2}")
        @Headers("Content-Type: application/json")
        TestClass testGet(URI uri, @Param("param1") String param1, @Param("param2") String param2);
    }

    @RegisterExtension
    static WireMockExtension wireMockHttp = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @Test
    void testHttp() throws URISyntaxException {
        wireMockHttp.stubFor(WireMock.get("/endpoint?param1=x&param2=y")
                .willReturn(WireMock.ok("{\"a\":\"hello\",\"b\":\"there\",\"c\":123,\"d\":456789}")));

        WireMockRuntimeInfo wmRuntimeInfo = wireMockHttp.getRuntimeInfo();

        TestClient testClient = FeignClientFactory.build(TestClient.class);
        TestClass result = testClient.testGet(new URI(wmRuntimeInfo.getHttpBaseUrl()), "x", "y");
        assertEquals(new TestClass("hello", "there", 123, 456789L), result);

        testClient = FeignClientFactory.build(TestClient.class, wmRuntimeInfo.getHttpBaseUrl());
        result = testClient.testGet(new URI(wmRuntimeInfo.getHttpBaseUrl()), "x", "y");
        assertEquals(new TestClass("hello", "there", 123, 456789L), result);
    }
}
