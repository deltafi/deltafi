/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.FlowMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Queries the Graphite render API for flow metrics.
 * Used by the member report endpoint to include aggregated metrics.
 */
@Service
@Slf4j
public class GraphiteQueryService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String graphiteUrl;
    private final boolean enabled;

    public GraphiteQueryService(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${GRAPHITE_QUERY_URL:http://deltafi-victoriametrics:8428/graphite}") String graphiteUrl,
            @Value("${deltafi.metrics.enabled:true}") boolean metricsEnabled) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.graphiteUrl = graphiteUrl.replaceAll("/$", "");
        this.enabled = metricsEnabled;
    }

    /**
     * Query flow metrics for the last N minutes.
     * Returns aggregated totals broken down by tag.
     */
    public FlowMetrics queryFlowMetrics(int lastMinutes) {
        if (!enabled) {
            return FlowMetrics.empty();
        }

        try {
            Map<String, Long> ingress = querySum(
                    "seriesByTag('name=stats_counts.bytes_from_source')",
                    "dataSource",
                    lastMinutes);

            Map<String, Long> egress = querySum(
                    "seriesByTag('name=stats_counts.bytes_to_sink')",
                    "dataSink",
                    lastMinutes);

            Map<String, Long> storage = queryLatest(
                    "seriesByTag('name=gauge.node.disk.usage')",
                    "service",
                    lastMinutes);

            Map<String, Long> deleted = querySum(
                    "seriesByTag('name=stats_counts.deleted.bytes')",
                    "policy",
                    lastMinutes);

            return new FlowMetrics(ingress, egress, storage, deleted);
        } catch (Exception e) {
            log.error("Failed to query flow metrics: {}", e.getMessage());
            return FlowMetrics.empty();
        }
    }

    /**
     * Query a metric and sum all datapoints, grouped by a tag.
     */
    private Map<String, Long> querySum(String target, String groupByTag, int lastMinutes) {
        String query = String.format("aliasByTags(%s, '%s')", target, groupByTag);
        List<GraphiteSeries> series = executeQuery(query, lastMinutes);

        Map<String, Long> result = new HashMap<>();
        for (GraphiteSeries s : series) {
            long sum = 0;
            for (List<Object> point : s.datapoints()) {
                if (point.get(0) != null) {
                    sum += ((Number) point.get(0)).longValue();
                }
            }
            result.put(s.target(), sum);
        }
        return result;
    }

    /**
     * Query a metric and get the latest value, grouped by a tag.
     */
    private Map<String, Long> queryLatest(String target, String groupByTag, int lastMinutes) {
        String query = String.format("aliasByTags(%s, '%s')", target, groupByTag);
        List<GraphiteSeries> series = executeQuery(query, lastMinutes);

        Map<String, Long> result = new HashMap<>();
        for (GraphiteSeries s : series) {
            // Find the latest non-null value
            Long latest = null;
            for (int i = s.datapoints().size() - 1; i >= 0; i--) {
                Object value = s.datapoints().get(i).get(0);
                if (value != null) {
                    latest = ((Number) value).longValue();
                    break;
                }
            }
            if (latest != null) {
                result.put(s.target(), latest);
            }
        }
        return result;
    }

    private List<GraphiteSeries> executeQuery(String target, int lastMinutes) {
        try {
            String encodedTarget = URLEncoder.encode(target, StandardCharsets.UTF_8);
            String url = String.format("%s/render?target=%s&from=-%dmin&format=json",
                    graphiteUrl, encodedTarget, lastMinutes);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Graphite query failed with status {}: {}", response.statusCode(), target);
                return List.of();
            }

            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to execute Graphite query '{}': {}", target, e.getMessage());
            return List.of();
        }
    }

    /**
     * Graphite render API response series.
     */
    private record GraphiteSeries(String target, List<List<Object>> datapoints) {}
}
