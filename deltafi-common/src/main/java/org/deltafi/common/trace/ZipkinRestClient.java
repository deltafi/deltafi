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
package org.deltafi.common.trace;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
public class ZipkinRestClient {
    private final String url;
    private final HttpClient httpClient;

    public ZipkinRestClient(String url) {
        this.url = url;
        this.httpClient = HttpClient.newHttpClient();
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