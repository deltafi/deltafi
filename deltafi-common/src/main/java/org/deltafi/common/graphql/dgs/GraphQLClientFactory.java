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
package org.deltafi.common.graphql.dgs;

import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.HttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphQLClientFactory {

    private final HttpClient httpClient;
    private final GraphqlClientProperties graphqlProperties;

    public GraphQLClient build(String... headers) {
        return GraphQLClient.createCustom(graphqlProperties.getCoreDomain(), (url, defaultHeaders, body) -> {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("content-type", MediaType.APPLICATION_JSON)
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if (headers.length > 0) {
                requestBuilder.headers(headers);
            }

            try {
                java.net.http.HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                return new HttpResponse(response.statusCode(), response.body());
            } catch (IOException ioException) {
                throw new DeltafiGraphQLException("Failed to make graphQL request", ioException);
            } catch (InterruptedException interruptedException) {
                log.error("Could not complete graphQL request", interruptedException);
                Thread.currentThread().interrupt();
                throw new DeltafiGraphQLException("GraphQL Request was interrupted");
            }
        });
    }
}
