package org.deltafi.actionkit.service;

import io.quarkus.runtime.StartupEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class HttpService {
    private final HttpClient httpClient;

    // Guarantee instantiation if not injected...
    @SuppressWarnings("EmptyMethod")
    void startup(@Observes StartupEvent event) {}

    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    public java.net.http.HttpResponse<InputStream> post(@NotNull String url, @NotNull Map<String, String> headers, @NotNull InputStream body) {
        Supplier<InputStream> is = () -> body;
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .setHeader("content-type", MediaType.APPLICATION_OCTET_STREAM)
                .POST(HttpRequest.BodyPublishers.ofInputStream(is));
        addHeaders(requestBuilder, headers);

        HttpRequest request = requestBuilder.build();

        try {
            // TODO: Should exceptions be thrown for 4xx return codes?
            return httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            // TODO: Make this better
            log.error("Error in http request", e);
            throw new RuntimeException(e.getMessage());
        }
    }

    private void addHeaders(@NotNull HttpRequest.Builder builder, @NotNull Map<String, String> headers) {
        if (!headers.isEmpty()) {
            // Flatten out the map to fit the silly, silly API
            builder.headers(
                    headers.entrySet().stream().flatMap(x -> Stream.of(x.getKey(), x.getValue())).toArray(String[]::new)
            );
        }
    }
}