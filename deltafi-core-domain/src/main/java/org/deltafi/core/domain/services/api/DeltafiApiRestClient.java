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
package org.deltafi.core.domain.services.api;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.services.api.model.DiskMetrics;
import org.json.JSONObject;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
public class DeltafiApiRestClient implements DeltafiApiClient {
    private final String url;
    private final HttpClient httpClient;

    private final static String CONTENT_METRICS_ENDPOINT = "/api/v1/metrics/system/content";

    public DeltafiApiRestClient(String url) {
        this.url = url;
        this.httpClient = HttpClient.newHttpClient();
    }

    /** Return metrics about the node hosting storage, or null if anything goes wrong contacting the API
     *
     * @return A DiskMetrics object containing the disk limit and usage
     */
    public DiskMetrics contentMetrics() {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url + CONTENT_METRICS_ENDPOINT))
                    .GET()
                    .headers("accept", MediaType.APPLICATION_JSON.toString()).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                String error = "Failed to retrieve node data from the DeltaFi API (" + url + "): " + response.body();
                log.error(error);
                throw new IOException(error);
            }
            JSONObject jsonObject = new JSONObject(response.body());
            JSONObject content = jsonObject.getJSONObject("content");
            return new DiskMetrics(content.getLong("limit"), content.getLong("usage"));
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }
}