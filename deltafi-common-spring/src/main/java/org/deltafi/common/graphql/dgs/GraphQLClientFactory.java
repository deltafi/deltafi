/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.graphql.dgs;

import com.netflix.graphql.dgs.client.DgsCustomGraphQLClient;
import com.netflix.graphql.dgs.client.DgsGraphQLClient;
import com.netflix.graphql.dgs.client.HttpResponse;
import com.netflix.graphql.dgs.client.Jackson2DgsJsonMapperAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

/** Creates configured {@link DgsGraphQLClient} instances that execute queries over HTTP. */
@RequiredArgsConstructor
@Slf4j
public class GraphQLClientFactory {
    private final HttpClient httpClient;

    /**
     * Build a {@link DgsGraphQLClient} that sends requests to the given URL.
     * @param coreUrl the GraphQL endpoint URL
     * @param headers optional header name/value pairs to include with each request
     * @return a configured {@link DgsGraphQLClient}
     */
    public DgsGraphQLClient build(String coreUrl, String... headers) {
        // DgsCustomGraphQLClient's 2-arg ctor defaults to the Jackson 3 mapper internally;
        // pass the Jackson 2 mapper explicitly via the 3-arg ctor to match our Jackson 2 stack.
        return new DgsCustomGraphQLClient(coreUrl, (url, defaultHeaders, body) -> {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", MediaType.APPLICATION_JSON)
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if (headers.length > 0) {
                requestBuilder.headers(headers);
            }

            try {
                java.net.http.HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                return new HttpResponse(response.statusCode(), response.body());
            } catch (IOException ioException) {
                throw new RuntimeException("Failed to make graphQL request", ioException);
            } catch (InterruptedException interruptedException) {
                log.error("Could not complete graphQL request", interruptedException);
                Thread.currentThread().interrupt();
                throw new RuntimeException("GraphQL Request was interrupted");
            }
        }, Jackson2DgsJsonMapperAdapter.Companion.fromOptions());
    }
}
