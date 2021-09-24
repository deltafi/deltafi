package org.deltafi.ingress.service;

import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.HttpResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;
import org.jetbrains.annotations.NotNull;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import static java.util.Collections.emptyMap;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class GraphQLClientService {
    private final GraphQLClient graphQLClient;
    private final HttpClient httpClient;

    public GraphQLResponse executeGraphQLQuery(GraphQLQueryRequest query) {
        return graphQLClient.executeQuery(query.serialize(), emptyMap(), (url, headers, body) -> getHttpResponse(url, body));
    }

    @NotNull
    private HttpResponse getHttpResponse(String url, String body) throws DeltafiGraphQLException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();

        try {
            java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return new HttpResponse(response.statusCode(), response.body());
        } catch (IOException e) {
            // throw an exception to force MinIO to retry this event
            throw new DeltafiGraphQLException("Failed to make graphQL request", e);
        } catch (InterruptedException ioException) {
            log.error("Could not complete graphQL request", ioException);
            Thread.currentThread().interrupt();
            throw new DeltafiGraphQLException("GraphQL Request was interrupted");
        }
    }
}
