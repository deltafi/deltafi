package org.deltafi.ingress.config;

import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.properties.GraphqlClientProperties;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;

import javax.enterprise.inject.Produces;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

@Slf4j
public class GraphQLClientConfig {
    @Produces
    public GraphQLClient graphQLClient(GraphqlClientProperties graphqlProperties, HttpClient httpClient) {
        return GraphQLClient.createCustom(graphqlProperties.getCoreDomain(), (url, headers, body) -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
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
