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
import org.deltafi.core.types.ActionMetrics;
import org.deltafi.core.types.FlowKey;
import org.deltafi.core.types.FlowMetrics;
import org.deltafi.core.types.PerFlowMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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
     * Query action metrics for specific actions.
     * Returns execution count and time for each action name provided.
     *
     * @param actionNames List of action names to query
     * @param lastMinutes Time interval in minutes
     */
    public List<ActionMetrics> queryActionMetrics(List<String> actionNames, int lastMinutes) {
        if (!enabled || actionNames == null || actionNames.isEmpty()) {
            return List.of();
        }

        List<ActionMetrics> results = new ArrayList<>();

        try {
            Map<String, Long> execCount = queryMetricByTag("stats_counts.action_execution", "source", lastMinutes);
            Map<String, Long> execTime = queryMetricByTag("stats_counts.action_execution_time_ms", "source", lastMinutes);

            for (String actionName : actionNames) {
                results.add(new ActionMetrics(
                        actionName,
                        execCount.getOrDefault(actionName, 0L),
                        execTime.getOrDefault(actionName, 0L)
                ));
            }
        } catch (Exception e) {
            log.error("Failed to query action metrics: {}", e.getMessage());
            for (String actionName : actionNames) {
                results.add(ActionMetrics.empty(actionName));
            }
        }

        return results;
    }

    /**
     * Query per-flow metrics for specific flows.
     * Returns files and bytes in/out for each flow provided.
     * Different flow types use different metric names and tags:
     * - Data sources: files_from_source/bytes_from_source tagged by dataSource
     * - Transforms: files_in/out, bytes_in/out tagged by flowName
     * - Data sinks: files_to_sink/bytes_to_sink tagged by dataSink
     *
     * @param flowKeys List of flow type/name pairs to query
     * @param lastMinutes Time interval in minutes
     */
    public List<PerFlowMetrics> queryPerFlowMetrics(List<FlowKey> flowKeys, int lastMinutes) {
        if (!enabled || flowKeys == null || flowKeys.isEmpty()) {
            return List.of();
        }

        List<PerFlowMetrics> results = new ArrayList<>();

        try {
            // Query all metric types we might need
            // Data source metrics (tagged by dataSource)
            Map<String, Long> filesFromSource = queryMetricByTag("stats_counts.files_from_source", "dataSource", lastMinutes);
            Map<String, Long> bytesFromSource = queryMetricByTag("stats_counts.bytes_from_source", "dataSource", lastMinutes);

            // Transform metrics (tagged by flowName)
            Map<String, Long> filesIn = queryMetricByTag("stats_counts.files_in", "flowName", lastMinutes);
            Map<String, Long> filesOut = queryMetricByTag("stats_counts.files_out", "flowName", lastMinutes);
            Map<String, Long> bytesIn = queryMetricByTag("stats_counts.bytes_in", "flowName", lastMinutes);
            Map<String, Long> bytesOut = queryMetricByTag("stats_counts.bytes_out", "flowName", lastMinutes);

            // Data sink metrics (tagged by dataSink)
            Map<String, Long> filesToSink = queryMetricByTag("stats_counts.files_to_sink", "dataSink", lastMinutes);
            Map<String, Long> bytesToSink = queryMetricByTag("stats_counts.bytes_to_sink", "dataSink", lastMinutes);

            for (FlowKey flowKey : flowKeys) {
                String flowType = flowKey.flowType();
                String flowName = flowKey.flowName();

                long fIn = 0, fOut = 0, bIn = 0, bOut = 0;

                if (isDataSource(flowType)) {
                    // Data sources: files/bytes from source (output only)
                    fOut = filesFromSource.getOrDefault(flowName, 0L);
                    bOut = bytesFromSource.getOrDefault(flowName, 0L);
                } else if ("DATA_SINK".equals(flowType)) {
                    // Data sinks: files/bytes to sink (input only)
                    fIn = filesToSink.getOrDefault(flowName, 0L);
                    bIn = bytesToSink.getOrDefault(flowName, 0L);
                } else if ("TRANSFORM".equals(flowType)) {
                    // Transforms: files/bytes in and out
                    fIn = filesIn.getOrDefault(flowName, 0L);
                    fOut = filesOut.getOrDefault(flowName, 0L);
                    bIn = bytesIn.getOrDefault(flowName, 0L);
                    bOut = bytesOut.getOrDefault(flowName, 0L);
                }

                results.add(new PerFlowMetrics(flowType, flowName, fIn, fOut, bIn, bOut));
            }
        } catch (Exception e) {
            log.error("Failed to query per-flow metrics: {}", e.getMessage());
            for (FlowKey flowKey : flowKeys) {
                results.add(PerFlowMetrics.empty(flowKey.flowType(), flowKey.flowName()));
            }
        }

        return results;
    }

    private boolean isDataSource(String flowType) {
        return "REST_DATA_SOURCE".equals(flowType) ||
               "TIMED_DATA_SOURCE".equals(flowType) ||
               "ON_ERROR_DATA_SOURCE".equals(flowType);
    }

    /**
     * Query a metric and sum all datapoints, grouped by a specific tag.
     */
    private Map<String, Long> queryMetricByTag(String metricName, String tagName, int lastMinutes) {
        String target = String.format("seriesByTag('name=%s')", metricName);
        return querySum(target, tagName, lastMinutes);
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
                if (point.getFirst() != null) {
                    sum += ((Number) point.getFirst()).longValue();
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
                Object value = s.datapoints().get(i).getFirst();
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
