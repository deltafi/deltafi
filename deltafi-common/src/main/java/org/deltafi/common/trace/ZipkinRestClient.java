package org.deltafi.common.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ZipkinRestClient {

    private static final Logger log = LoggerFactory.getLogger(ZipkinRestClient.class);

    private final HttpClient httpClient;
    private final String url;

    public ZipkinRestClient(HttpClient httpClient, String url) {
        this.httpClient = httpClient;
        this.url = url;
    }

    void sendSpan(String spanJson) {
        httpClient.sendAsync(buildRequest(spanJson), HttpResponse.BodyHandlers.ofString())
            .thenAcceptAsync(response -> processResponse(response, spanJson))
            .exceptionally(throwable -> processException(throwable, spanJson));
    }

    public HttpRequest buildRequest(String spanJson) {
        return HttpRequest.newBuilder().uri(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(spanJson)).build();
    }

    public void processResponse(HttpResponse<String> response, String spanJson) {
        if (response.statusCode() < 200 || response.statusCode() > 299 && log.isErrorEnabled()) {
            log.error("Failed to send {} to Zipkin with error of {}", spanJson, response.body());
        }
    }

    public Void processException(Throwable throwable, String spanJson) {
        log.error("Failed to send {} to Zipkin with an exception", spanJson, throwable);
        return null;
    }

}