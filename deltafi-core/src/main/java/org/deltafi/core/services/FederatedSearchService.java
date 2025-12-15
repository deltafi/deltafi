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
import org.deltafi.common.types.KeyValue;
import org.deltafi.core.generated.types.DeltaFilesFilter;
import org.deltafi.core.types.DeltaFiles;
import org.deltafi.core.types.leader.*;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ABOUTME: Executes federated searches across all member systems.
 * ABOUTME: Returns aggregated match counts per system for the given filter criteria.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FederatedSearchService {
    private static final int MAX_CONCURRENT_REQUESTS = 50;
    private static final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);

    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final DeltaFilesService deltaFilesService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * Execute a federated search across all configured members.
     * Queries the leader locally and all members in parallel via GraphQL.
     */
    public FederatedSearchResponse search(DeltaFilesFilter filter) {
        List<FederatedSearchResult> results = new CopyOnWriteArrayList<>();

        // Query leader locally
        try {
            DeltaFiles leaderResults = deltaFilesService.deltaFiles(0, 0, filter, null, null);
            results.add(new FederatedSearchResult(
                "Leader",
                "local",
                List.of("leader"),
                (long) leaderResults.getTotalCount(),
                ConnectionState.CONNECTED,
                null
            ));
        } catch (Exception e) {
            log.error("Failed to query leader: {}", e.getMessage(), e);
            results.add(new FederatedSearchResult(
                "Leader",
                "local",
                List.of("leader"),
                null,
                ConnectionState.UNREACHABLE,
                e.getMessage()
            ));
        }

        // Query all members in parallel
        List<MemberConfig> configs = deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs();
        int timeoutMs = deltaFiPropertiesService.getDeltaFiProperties().getMemberRequestTimeout();

        List<CompletableFuture<Void>> futures = configs.stream()
            .map(config -> CompletableFuture
                .supplyAsync(() -> queryMember(config, filter), executor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> handleMemberError(config, ex))
                .thenAccept(results::add))
            .toList();

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return buildResponse(results);
    }

    private FederatedSearchResult queryMember(MemberConfig config, DeltaFilesFilter filter) {
        try {
            String query = buildGraphQLQuery(filter);
            Long count = executeGraphQLQuery(config, query);
            return new FederatedSearchResult(
                config.name(),
                config.url(),
                config.tags(),
                count,
                ConnectionState.CONNECTED,
                null
            );
        } catch (Exception e) {
            log.error("Failed to query member {}: {}", config.name(), e.getMessage());
            return new FederatedSearchResult(
                config.name(),
                config.url(),
                config.tags(),
                null,
                ConnectionState.UNREACHABLE,
                formatException(e)
            );
        }
    }

    private FederatedSearchResult handleMemberError(MemberConfig config, Throwable ex) {
        String error = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        if (ex instanceof TimeoutException || (ex.getCause() != null && ex.getCause() instanceof TimeoutException)) {
            error = "Request timed out";
        }
        return new FederatedSearchResult(
            config.name(),
            config.url(),
            config.tags(),
            null,
            ConnectionState.UNREACHABLE,
            error
        );
    }

    private String buildGraphQLQuery(DeltaFilesFilter filter) {
        StringBuilder filterBuilder = new StringBuilder();
        List<String> filterParts = new ArrayList<>();

        // Build filter object fields
        if (filter.getModifiedAfter() != null) {
            filterParts.add("modifiedAfter: \"" + filter.getModifiedAfter() + "\"");
        }
        if (filter.getModifiedBefore() != null) {
            filterParts.add("modifiedBefore: \"" + filter.getModifiedBefore() + "\"");
        }
        if (filter.getCreatedAfter() != null) {
            filterParts.add("createdAfter: \"" + filter.getCreatedAfter() + "\"");
        }
        if (filter.getCreatedBefore() != null) {
            filterParts.add("createdBefore: \"" + filter.getCreatedBefore() + "\"");
        }
        if (filter.getNameFilter() != null && filter.getNameFilter().getName() != null) {
            filterParts.add("nameFilter: {name: \"" + escapeGraphQL(filter.getNameFilter().getName()) + "\"}");
        }
        if (filter.getStage() != null) {
            filterParts.add("stage: " + filter.getStage().name());
        }
        if (filter.getDataSources() != null && !filter.getDataSources().isEmpty()) {
            filterParts.add("dataSources: " + toGraphQLStringArray(filter.getDataSources()));
        }
        if (filter.getDataSinks() != null && !filter.getDataSinks().isEmpty()) {
            filterParts.add("dataSinks: " + toGraphQLStringArray(filter.getDataSinks()));
        }
        if (filter.getTransforms() != null && !filter.getTransforms().isEmpty()) {
            filterParts.add("transforms: " + toGraphQLStringArray(filter.getTransforms()));
        }
        if (filter.getTopics() != null && !filter.getTopics().isEmpty()) {
            filterParts.add("topics: " + toGraphQLStringArray(filter.getTopics()));
        }
        if (filter.getAnnotations() != null && !filter.getAnnotations().isEmpty()) {
            filterParts.add("annotations: " + toGraphQLKeyValueArray(filter.getAnnotations()));
        }
        if (filter.getEgressed() != null) {
            filterParts.add("egressed: " + filter.getEgressed());
        }
        if (filter.getFiltered() != null) {
            filterParts.add("filtered: " + filter.getFiltered());
        }
        if (filter.getTestMode() != null) {
            filterParts.add("testMode: " + filter.getTestMode());
        }
        if (filter.getReplayable() != null) {
            filterParts.add("replayable: " + filter.getReplayable());
        }
        if (filter.getTerminalStage() != null) {
            filterParts.add("terminalStage: " + filter.getTerminalStage());
        }
        if (filter.getPendingAnnotations() != null) {
            filterParts.add("pendingAnnotations: " + filter.getPendingAnnotations());
        }
        if (filter.getPaused() != null) {
            filterParts.add("paused: " + filter.getPaused());
        }
        if (filter.getPinned() != null) {
            filterParts.add("pinned: " + filter.getPinned());
        }
        if (filter.getWarnings() != null) {
            filterParts.add("warnings: " + filter.getWarnings());
        }
        if (filter.getIngressBytesMin() != null) {
            filterParts.add("ingressBytesMin: " + filter.getIngressBytesMin());
        }
        if (filter.getIngressBytesMax() != null) {
            filterParts.add("ingressBytesMax: " + filter.getIngressBytesMax());
        }
        if (filter.getTotalBytesMin() != null) {
            filterParts.add("totalBytesMin: " + filter.getTotalBytesMin());
        }
        if (filter.getTotalBytesMax() != null) {
            filterParts.add("totalBytesMax: " + filter.getTotalBytesMax());
        }
        if (filter.getRequeueCountMin() != null) {
            filterParts.add("requeueCountMin: " + filter.getRequeueCountMin());
        }
        if (filter.getFilteredCause() != null) {
            filterParts.add("filteredCause: \"" + escapeGraphQL(filter.getFilteredCause()) + "\"");
        }
        if (filter.getErrorCause() != null) {
            filterParts.add("errorCause: \"" + escapeGraphQL(filter.getErrorCause()) + "\"");
        }
        if (filter.getErrorAcknowledged() != null) {
            filterParts.add("errorAcknowledged: " + filter.getErrorAcknowledged());
        }

        String filterString = filterParts.isEmpty() ? "" : ", filter: {" + String.join(", ", filterParts) + "}";

        return "query federatedSearch { deltaFiles(limit: 0" + filterString + ") { totalCount } }";
    }

    private String toGraphQLStringArray(List<String> values) {
        return "[" + values.stream()
            .map(v -> "\"" + escapeGraphQL(v) + "\"")
            .collect(Collectors.joining(", ")) + "]";
    }

    private String toGraphQLKeyValueArray(List<KeyValue> annotations) {
        return "[" + annotations.stream()
            .map(kv -> "{key: \"" + escapeGraphQL(kv.getKey()) + "\", value: \"" + escapeGraphQL(kv.getValue()) + "\"}")
            .collect(Collectors.joining(", ")) + "]";
    }

    private String escapeGraphQL(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Long executeGraphQLQuery(MemberConfig config, String query) throws Exception {
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

        JsonNode jsonNode = objectMapper.readTree(response.body());
        JsonNode totalCount = jsonNode.path("data").path("deltaFiles").path("totalCount");
        if (totalCount.isMissingNode()) {
            throw new RuntimeException("GraphQL query failed: missing totalCount in response");
        }

        return totalCount.asLong();
    }

    private FederatedSearchResponse buildResponse(List<FederatedSearchResult> results) {
        long totalCount = results.stream()
            .filter(r -> r.count() != null)
            .mapToLong(FederatedSearchResult::count)
            .sum();

        int membersSearched = (int) results.stream()
            .filter(r -> r.status() == ConnectionState.CONNECTED)
            .count();

        int membersFailed = (int) results.stream()
            .filter(r -> r.status() != ConnectionState.CONNECTED)
            .count();

        return new FederatedSearchResponse(
            results,
            totalCount,
            membersSearched,
            membersFailed,
            OffsetDateTime.now()
        );
    }

    private String formatException(Exception e) {
        String message = e.getMessage();
        if (message == null) return "Unknown error";

        if (message.contains("Connection refused")) return "Connection refused";
        if (message.contains("timed out")) return "Connection timed out";
        if (message.contains("UnknownHostException")) return "Host not found";

        return message.replaceAll("java\\.\\w+\\.\\w+Exception: ", "");
    }
}
