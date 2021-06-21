package org.deltafi.common.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(spanJson)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() > 299 && log.isErrorEnabled()) {
                log.error("Failed to send {} to Zipkin with error of {}", spanJson, response.body());
            }
        } catch (IOException ioException) {
            log.error("Failed to send {} to Zipkin", spanJson, ioException);
        } catch (InterruptedException e) {
            log.error("Could not complete sending the span: {}", spanJson, e);
            Thread.currentThread().interrupt();
        }
    }



}