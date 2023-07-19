/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services.api;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.exceptions.DeltafiApiException;
import org.deltafi.core.services.api.model.DiskMetrics;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
public class DeltafiApiRestClient implements DeltafiApiClient {

    private final String url;
    private final HttpClient httpClient;
    private final static String CONTENT_METRICS_ENDPOINT = "/api/v1/metrics/system/content";
    private final static String EVENTS_ENDPOINT = "/api/v1/events";
    private final static String METRIC_VIEW_PERMISSION = "MetricsView";

    public DeltafiApiRestClient(@Value("${API_URL:http://deltafi-api-service}") String url) {
        this.url = url;
        this.httpClient = HttpClient.newHttpClient();
    }

    /** Return metrics about the node hosting storage, or null if anything goes wrong contacting the API
     *
     * @return A DiskMetrics object containing the disk limit and usage
     */
    public DiskMetrics contentMetrics() throws DeltafiApiException {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url + CONTENT_METRICS_ENDPOINT))
                    .GET()
                    .headers("accept", MediaType.APPLICATION_JSON.toString(), DeltaFiConstants.PERMISSIONS_HEADER, METRIC_VIEW_PERMISSION).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                String error = "Failed to retrieve node data from the DeltaFi API (" + url + "): " + response.body();
                log.error(error);
                throw new IOException(error);
            }
            JSONObject jsonObject = new JSONObject(response.body());
            JSONObject content = jsonObject.getJSONObject("content");

            long limit = content.getLong("limit");
            long usage = content.getLong("usage");
            if (limit == 0 || usage == 0) {
                log.error("Received invalid disk metrics from API");
                throw new DeltafiApiException("Received invalid disk metrics from API");
            }

            return new DiskMetrics(limit, usage);
        } catch (ConnectException e) {
            log.error("Unable to connect to DeltaFi API");
            throw new DeltafiApiException("Unable to connect to API", e);
        } catch (Exception e) {
            log.error("DeltaFi API communication error", e);
            throw new DeltafiApiException("Unable to communicate with API", e);
        }
    }

    public String createEvent(String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url + EVENTS_ENDPOINT))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .headers("accept", MediaType.APPLICATION_JSON.toString(), DeltaFiConstants.PERMISSIONS_HEADER, "Admin").build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                String error = "Unable to post to the event API (Error " + response.statusCode() + "):\n" + body;
                log.error(error);
            }
            return response.body();
        } catch (Throwable e) {
            log.error("Unable to post a new event",e);
        }
        return null;
    }
}