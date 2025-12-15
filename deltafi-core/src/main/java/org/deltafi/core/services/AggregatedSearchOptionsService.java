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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.leader.AggregatedSearchOptions;
import org.deltafi.core.types.leader.MemberConfig;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * ABOUTME: Aggregates search filter options from all fleet members.
 * ABOUTME: Fetches flow names, topics, and annotation keys from each system.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AggregatedSearchOptionsService {
    private static final int MAX_CONCURRENT_REQUESTS = 50;
    private static final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);

    private static final String FLOW_NAMES_QUERY = """
        query {
            getFlowNames {
                restDataSource
                timedDataSource
                dataSink
                transform
            }
        }
        """;

    private static final String TOPICS_QUERY = """
        query {
            getAllTopics {
                name
            }
        }
        """;

    private static final String ANNOTATION_KEYS_QUERY = """
        query {
            annotationKeys
        }
        """;

    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;
    private final DataSinkService dataSinkService;
    private final TransformFlowService transformFlowService;
    private final FlowCacheService flowCacheService;
    private final DeltaFilesService deltaFilesService;

    /**
     * Fetch aggregated search options from all configured members plus the leader.
     */
    public AggregatedSearchOptions getAggregatedOptions() {
        Set<String> restDataSources = ConcurrentHashMap.newKeySet();
        Set<String> timedDataSources = ConcurrentHashMap.newKeySet();
        Set<String> dataSinks = ConcurrentHashMap.newKeySet();
        Set<String> transforms = ConcurrentHashMap.newKeySet();
        Set<String> topics = ConcurrentHashMap.newKeySet();
        Set<String> annotationKeys = ConcurrentHashMap.newKeySet();

        // Add local leader options
        addLocalOptions(restDataSources, timedDataSources, dataSinks, transforms, topics, annotationKeys);

        // Query all members in parallel
        List<MemberConfig> configs = deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs();
        int timeoutMs = deltaFiPropertiesService.getDeltaFiProperties().getMemberRequestTimeout();

        List<CompletableFuture<Void>> futures = configs.stream()
            .map(config -> CompletableFuture.runAsync(() ->
                fetchMemberOptions(config, restDataSources, timedDataSources, dataSinks, transforms, topics, annotationKeys), executor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("Failed to fetch options from member {}: {}", config.name(), ex.getMessage());
                    return null;
                }))
            .toList();

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return new AggregatedSearchOptions(
            new TreeSet<>(restDataSources),
            new TreeSet<>(timedDataSources),
            new TreeSet<>(dataSinks),
            new TreeSet<>(transforms),
            new TreeSet<>(topics),
            new TreeSet<>(annotationKeys)
        );
    }

    private void addLocalOptions(Set<String> restDataSources, Set<String> timedDataSources,
                                  Set<String> dataSinks, Set<String> transforms,
                                  Set<String> topics, Set<String> annotationKeys) {
        try {
            restDataSources.addAll(restDataSourceService.getFlowNamesByState(null));
            timedDataSources.addAll(timedDataSourceService.getFlowNamesByState(null));
            dataSinks.addAll(dataSinkService.getFlowNamesByState(null));
            transforms.addAll(transformFlowService.getFlowNamesByState(null));
        } catch (Exception e) {
            log.error("Failed to get local flow names: {}", e.getMessage());
        }

        try {
            flowCacheService.refreshCache();
            flowCacheService.getTopics().forEach(topic -> topics.add(topic.getName()));
        } catch (Exception e) {
            log.error("Failed to get local topics: {}", e.getMessage());
        }

        try {
            annotationKeys.addAll(deltaFilesService.annotationKeys());
        } catch (Exception e) {
            log.error("Failed to get local annotation keys: {}", e.getMessage());
        }
    }

    private void fetchMemberOptions(MemberConfig config,
                                     Set<String> restDataSources, Set<String> timedDataSources,
                                     Set<String> dataSinks, Set<String> transforms,
                                     Set<String> topics, Set<String> annotationKeys) {
        // Fetch flow names
        try {
            JsonNode flowData = executeGraphQL(config, FLOW_NAMES_QUERY);
            JsonNode flowNames = flowData.path("data").path("getFlowNames");
            addStringsFromArray(flowNames.path("restDataSource"), restDataSources);
            addStringsFromArray(flowNames.path("timedDataSource"), timedDataSources);
            addStringsFromArray(flowNames.path("dataSink"), dataSinks);
            addStringsFromArray(flowNames.path("transform"), transforms);
        } catch (Exception e) {
            log.warn("Failed to fetch flow names from {}: {}", config.name(), e.getMessage());
        }

        // Fetch topics
        try {
            JsonNode topicsData = executeGraphQL(config, TOPICS_QUERY);
            JsonNode allTopics = topicsData.path("data").path("getAllTopics");
            if (allTopics.isArray()) {
                for (JsonNode topic : allTopics) {
                    String name = topic.path("name").asText(null);
                    if (name != null) topics.add(name);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch topics from {}: {}", config.name(), e.getMessage());
        }

        // Fetch annotation keys
        try {
            JsonNode annotationData = executeGraphQL(config, ANNOTATION_KEYS_QUERY);
            addStringsFromArray(annotationData.path("data").path("annotationKeys"), annotationKeys);
        } catch (Exception e) {
            log.warn("Failed to fetch annotation keys from {}: {}", config.name(), e.getMessage());
        }
    }

    private void addStringsFromArray(JsonNode arrayNode, Set<String> target) {
        if (arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                String value = node.asText(null);
                if (value != null && !value.isEmpty()) {
                    target.add(value);
                }
            }
        }
    }

    private JsonNode executeGraphQL(MemberConfig config, String query) throws Exception {
        String url = config.url() + "/api/v2/graphql";
        String body = objectMapper.writeValueAsString(Map.of("query", query));

        Duration requestTimeout = Duration.ofMillis(deltaFiPropertiesService.getDeltaFiProperties().getMemberRequestTimeout());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");

        // Add authentication if configured
        if (config.credentials() != null && "basic".equals(config.credentials().type())) {
            String password = System.getenv(config.credentials().passwordEnvVar());
            if (password != null) {
                String credentials = config.credentials().username() + ":" + password;
                String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
                builder.header("Authorization", "Basic " + encoded);
            }
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " from " + url);
        }

        return objectMapper.readTree(response.body());
    }
}
