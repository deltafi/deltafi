package org.deltafi.actionkit.service;

import com.netflix.graphql.dgs.client.HttpResponse;
import com.netflix.graphql.dgs.client.RequestExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.exception.DgsPostException;
import org.jetbrains.annotations.NotNull;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class DgsPostService implements RequestExecutor {
    private final HttpClient httpClient;

    private boolean healthy = true;

    // This is a special method specifically for GraphQLClient queries
    @NotNull
    @Override
    public HttpResponse execute(@NotNull String url, @NotNull Map<String, ? extends List<String>> headers, @NotNull String body) throws DgsPostException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .headers("content-type", MediaType.APPLICATION_JSON).build();

        try {
            java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            healthy = true;
            return new HttpResponse(response.statusCode(), response.body());

        } catch (IOException | InterruptedException e) {
            if(healthy) {
                healthy = false;
                log.error("Unable to communicate with DGS: " + e.getMessage());
                log.debug("DGS post exception", e);
            }
            throw new DgsPostException("Unable to issue GraphQL request: " + e.getMessage());
        }
    }
}