/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;

import static org.deltafi.common.constant.DeltaFiConstants.PERMISSIONS_HEADER;
import static org.deltafi.common.constant.DeltaFiConstants.USER_NAME_HEADER;

@Slf4j
@Service
public class AuthProxyService {

    private static final ResponseEntity<String> FAILED_REQUEST =
            new ResponseEntity<>("Failed to complete the request", new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpClient httpClient;
    private final String authUrl;

    public AuthProxyService(HttpClient httpClient, @Value("${DELTAFI_AUTH_URL:http://deltafi-auth-service}") String authUrl) {
        this.httpClient = httpClient;
        this.authUrl = authUrl;
    }

    public ResponseEntity<String> proxyRequest(HttpServletRequest request) {
        try {
            return doProxyRequest(request);
        } catch (InterruptedException e) {
            log.error("Request interrupted", e);
            Thread.currentThread().interrupt();
            return FAILED_REQUEST;
        } catch (Exception e) {
            log.error("Failed to make request", e);
            return FAILED_REQUEST;
        }
    }

    private ResponseEntity<String> doProxyRequest(HttpServletRequest request) throws IOException, InterruptedException {
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();

        String targetUrl = authUrl + requestUri.replace("/api/v2", "")
                + (queryString != null ? "?" + queryString : "");

        log.trace("Proxied {} to {} ", request.getMethod(), targetUrl);

        HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl));

        httpRequestBuilder.header(USER_NAME_HEADER, request.getHeader(USER_NAME_HEADER));
        httpRequestBuilder.header(PERMISSIONS_HEADER, request.getHeader(PERMISSIONS_HEADER));

        if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod())) {
            InputStream requestBodyStream = request.getInputStream();
            httpRequestBuilder.method(request.getMethod(), BodyPublishers.ofInputStream(() -> requestBodyStream));
        } else {
            httpRequestBuilder.method(request.getMethod(), HttpRequest.BodyPublishers.noBody());
        }

        HttpRequest httpRequest = httpRequestBuilder.build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        HttpHeaders responseHeaders = new HttpHeaders();
        response.headers().map().forEach((key, values) -> values.forEach(value -> responseHeaders.add(key, value)));
        return new ResponseEntity<>(response.body(), responseHeaders, response.statusCode());
    }
}
