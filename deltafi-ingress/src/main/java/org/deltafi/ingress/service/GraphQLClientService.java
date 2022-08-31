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
package org.deltafi.ingress.service;

import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.properties.GraphqlClientProperties;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

@Slf4j
@Service
public class GraphQLClientService {

    HttpClient httpClient;
    GraphqlClientProperties graphqlProperties;

    public GraphQLClientService(HttpClient httpClient, GraphqlClientProperties graphqlProperties) {
        this.httpClient = httpClient;
        this.graphqlProperties = graphqlProperties;
    }

    public GraphQLClient graphQLClient(final String username) {
        return GraphQLClient.createCustom(graphqlProperties.getCoreDomain(), (url, headers, body) -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .headers("content-type", MediaType.APPLICATION_JSON, DeltaFiConstants.USER_HEADER, username)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            try {
                java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
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
