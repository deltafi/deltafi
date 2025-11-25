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
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.core.generated.types.DeltaFileStats;
import org.deltafi.core.monitor.MonitorResult;
import org.deltafi.core.types.FlowMetrics;
import org.deltafi.core.types.MemberReport;
import org.deltafi.core.types.NodeMetrics;
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

/**
 * Exception thrown when an HTTP request fails with a specific status code.
 */
class HttpStatusException extends Exception {
    private final int statusCode;

    HttpStatusException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    int getStatusCode() {
        return statusCode;
    }
}

/**
 * Service for monitoring member DeltaFi instances in a leader-member deployment.
 * Polls members for status, metrics, and health checks, caching results in Valkey.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberMonitorService {
    private static final String VALKEY_KEY_PREFIX = "org.deltafi.leader.member.";
    private static final int MAX_CONCURRENT_REQUESTS = 50;
    private static final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
    private static final Duration FLOW_METRICS_CACHE_DURATION = Duration.ofSeconds(5);

    // Cache for flow metrics: key is minutes, value is cached result with timestamp
    private record FlowMetricsCache(Map<String, FlowMetrics> data, long timestamp) {}
    private final ConcurrentHashMap<Integer, FlowMetricsCache> flowMetricsCache = new ConcurrentHashMap<>();
    private static final long FLOW_METRICS_CACHE_MAX_AGE_MS = FLOW_METRICS_CACHE_DURATION.toMillis();

    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final ValkeyKeyedBlockingQueue valkeyQueue;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final SystemService systemService;
    private final DeltaFilesService deltaFilesService;
    private final org.springframework.boot.info.BuildProperties buildProperties;
    private final GraphiteQueryService graphiteQueryService;

    /**
     * Poll a single member for all metrics.
     * Tries new unified endpoint first, falls back to legacy 3-call approach if not available.
     * This is a blocking call - the caller is responsible for async execution if needed.
     */
    public MemberStatus pollMember(MemberConfig memberConfig) {
        try {
            MonitorResult status;
            Long errorCount;
            Long inFlightCount;
            Long warmQueuedCount = null;
            Long coldQueuedCount = null;
            Long pausedCount = null;
            Double cpuUsage = null;
            Double memoryUsage = null;
            Double diskUsage = null;
            String version = null;

            // Try new unified endpoint first
            try {
                MemberReport report = fetchMemberStatusReport(memberConfig);
                status = objectMapper.treeToValue(report.status(), MonitorResult.class);
                errorCount = report.errorCount();
                inFlightCount = report.inFlightCount();
                warmQueuedCount = report.warmQueuedCount();
                coldQueuedCount = report.coldQueuedCount();
                pausedCount = report.pausedCount();
                cpuUsage = report.cpuUsage();
                memoryUsage = report.memoryUsage();
                diskUsage = report.diskUsage();
                version = report.version();
            } catch (HttpStatusException e) {
                // Check if it's a 404 (endpoint doesn't exist), fall back to legacy approach
                if (e.getStatusCode() == 404) {
                    log.debug("Member {} doesn't support /status/report, using legacy 3-call approach", memberConfig.name());
                    status = fetchMemberStatus(memberConfig);
                    errorCount = fetchMemberErrorCount(memberConfig);
                    inFlightCount = fetchMemberInFlightCount(memberConfig);
                    // warmQueuedCount, coldQueuedCount, pausedCount, system metrics, version will be null for legacy approach
                } else {
                    // Some other HTTP error, rethrow
                    throw e;
                }
            }

            // Build status object
            MemberStatus memberStatus = new MemberStatus(
                memberConfig.name(),
                memberConfig.url(),
                memberConfig.tags(),
                false, // not leader
                status,
                errorCount,
                inFlightCount,
                warmQueuedCount,
                coldQueuedCount,
                pausedCount,
                cpuUsage,
                memoryUsage,
                diskUsage,
                OffsetDateTime.now(),
                ConnectionState.CONNECTED,
                null,
                version
            );

            // Store in Valkey
            storeInValkey(memberConfig.name(), memberStatus);

            return memberStatus;

        } catch (Exception e) {
            log.error("Failed to poll member {}: {}", memberConfig.name(), e.getMessage(), e);
            String cleanError = formatException(e);
            MemberStatus failureStatus = handlePollFailure(memberConfig, cleanError);
            storeInValkey(memberConfig.name(), failureStatus);
            return failureStatus;
        }
    }

    /**
     * Handle polling failure by returning cached data or unreachable status.
     */
    private MemberStatus handlePollFailure(MemberConfig memberConfig, String errorMessage) {
        MemberStatus cachedStatus = loadFromValkey(memberConfig.name());
        if (cachedStatus != null) {
            // Mark as stale
            return new MemberStatus(
                cachedStatus.memberName(),
                cachedStatus.url(),
                cachedStatus.tags(),
                cachedStatus.isLeader(),
                cachedStatus.status(),
                cachedStatus.errorCount(),
                cachedStatus.inFlightCount(),
                cachedStatus.warmQueuedCount(),
                cachedStatus.coldQueuedCount(),
                cachedStatus.pausedCount(),
                cachedStatus.cpuUsage(),
                cachedStatus.memoryUsage(),
                cachedStatus.diskUsage(),
                cachedStatus.lastUpdated(),
                ConnectionState.STALE,
                errorMessage,
                cachedStatus.version()
            );
        } else {
            // No cached data, return unreachable
            return new MemberStatus(
                memberConfig.name(),
                memberConfig.url(),
                memberConfig.tags(),
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                ConnectionState.UNREACHABLE,
                errorMessage,
                null
            );
        }
    }

    /**
     * Get leader's own status (from local system).
     */
    public MemberStatus getLeaderStatus() {
        SystemService.Status localStatus = systemService.systemStatus();
        DeltaFileStats stats = deltaFilesService.deltaFileStats();
        Long errorCount = deltaFilesService.countUnacknowledgedErrors();
        String version = buildProperties.getVersion();

        // Convert Status to MonitorResult
        MonitorResult monitorResult = convertToMonitorResult(localStatus);

        // Collect system metrics from standard metrics collection
        List<NodeMetrics> nodes = systemService.nodeAppsAndMetrics();
        Double cpuUsage = getCpuUsage(nodes);
        Double memoryUsage = getMemoryUsage(nodes);
        Double diskUsage = getDiskUsage(nodes);

        return new MemberStatus(
            "Leader",
            "local",
            List.of("leader"),
            true,
            monitorResult,
            errorCount,
            stats.getInFlightCount(),
            stats.getWarmQueuedCount(),
            stats.getColdQueuedCount(),
            stats.getPausedCount(),
            cpuUsage,
            memoryUsage,
            diskUsage,
            OffsetDateTime.now(),
            ConnectionState.CONNECTED,
            null,
            version
        );
    }

    private Double getCpuUsage(List<NodeMetrics> nodes) {
        long totalUsage = 0;
        long totalLimit = 0;
        for (NodeMetrics node : nodes) {
            Map<String, Long> cpu = node.resources().get("cpu");
            if (cpu != null) {
                Long usage = cpu.get("usage");
                Long limit = cpu.get("limit");
                if (usage != null) totalUsage += usage;
                if (limit != null) totalLimit += limit;
            }
        }
        if (totalLimit > 0) {
            return Math.round((double) totalUsage / totalLimit * 1000.0) / 10.0;
        }
        return null;
    }

    private Double getMemoryUsage(List<NodeMetrics> nodes) {
        long totalUsage = 0;
        long totalLimit = 0;
        for (NodeMetrics node : nodes) {
            Map<String, Long> memory = node.resources().get("memory");
            if (memory != null) {
                Long usage = memory.get("usage");
                Long limit = memory.get("limit");
                if (usage != null) totalUsage += usage;
                if (limit != null) totalLimit += limit;
            }
        }
        if (totalLimit > 0) {
            return Math.round((double) totalUsage / totalLimit * 1000.0) / 10.0;
        }
        return null;
    }

    private Double getDiskUsage(List<NodeMetrics> nodes) {
        long totalUsage = 0;
        long totalLimit = 0;
        for (NodeMetrics node : nodes) {
            Map<String, Long> disk = node.resources().get("disk");
            if (disk != null) {
                Long usage = disk.get("usage");
                Long limit = disk.get("limit");
                if (usage != null) totalUsage += usage;
                if (limit != null) totalLimit += limit;
            }
        }
        if (totalLimit > 0) {
            return Math.round((double) totalUsage / totalLimit * 1000.0) / 10.0;
        }
        return null;
    }

    /**
     * Get all member statuses (from Valkey + leader).
     */
    public LeaderDashboardData getAllMemberStatuses() {
        List<MemberStatus> members = new ArrayList<>();

        // Add leader
        members.add(getLeaderStatus());

        // Add all members from Valkey
        for (String memberName : getMemberNames()) {
            MemberStatus status = loadFromValkey(memberName);
            if (status != null) {
                members.add(status);
            }
        }

        return new LeaderDashboardData(members, OffsetDateTime.now());
    }

    private MemberReport fetchMemberStatusReport(MemberConfig config) throws Exception {
        String url = config.url() + "/api/v2/status/report";
        HttpRequest request = buildRequest(config, url, null);

        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            boolean hasAuth = request.headers().firstValue("Authorization").isPresent();
            log.warn("HTTP {} from {}, Authorization header present: {}",
                response.statusCode(), url, hasAuth);
            throw new HttpStatusException(response.statusCode(), formatHttpError(response.statusCode(), url));
        }

        return objectMapper.readValue(response.body(), MemberReport.class);
    }

    private MonitorResult fetchMemberStatus(MemberConfig config) throws Exception {
        String url = config.url() + "/api/v2/status";
        HttpRequest request = buildRequest(config, url, null);

        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            boolean hasAuth = request.headers().firstValue("Authorization").isPresent();
            log.warn("HTTP {} from {}, Authorization header present: {}",
                response.statusCode(), url, hasAuth);
            throw new RuntimeException(formatHttpError(response.statusCode(), url));
        }

        // Parse Status response and extract MonitorResult
        JsonNode statusNode = objectMapper.readTree(response.body());
        JsonNode statusField = statusNode.get("status");

        return objectMapper.treeToValue(statusField, MonitorResult.class);
    }

    private Long fetchMemberErrorCount(MemberConfig config) throws Exception {
        String query = """
            query getErrorCount {
              deltaFiles(
                filter: {stage: ERROR, errorAcknowledged: false, modifiedAfter: "1970-01-01T00:00:00.000Z"}
              ) {
                totalCount
              }
            }
            """;

        return executeGraphQLQuery(config, query, "data.deltaFiles.totalCount", Long.class);
    }

    private Long fetchMemberInFlightCount(MemberConfig config) throws Exception {
        String query = """
            query getInFlightCount {
              deltaFileStats {
                inFlightCount
              }
            }
            """;

        return executeGraphQLQuery(config, query, "data.deltaFileStats.inFlightCount", Long.class);
    }

    private <T> T executeGraphQLQuery(MemberConfig config, String query, String jsonPath, Class<T> resultType) throws Exception {
        String url = config.url() + "/api/v2/graphql";
        String body = objectMapper.writeValueAsString(Map.of("query", query));

        HttpRequest request = buildRequest(config, url, body);

        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            boolean hasAuth = request.headers().firstValue("Authorization").isPresent();
            log.warn("HTTP {} from {} (GraphQL), Authorization header present: {}",
                response.statusCode(), url, hasAuth);
            throw new RuntimeException(formatHttpError(response.statusCode(), url));
        }

        // Parse GraphQL response
        JsonNode jsonNode = objectMapper.readTree(response.body());

        // Navigate JSON path
        String[] parts = jsonPath.split("\\.");
        JsonNode current = jsonNode;
        for (String part : parts) {
            current = current.get(part);
            if (current == null) {
                throw new RuntimeException("GraphQL query failed: missing field '" + part + "' in response");
            }
        }

        return objectMapper.convertValue(current, resultType);
    }

    private String formatHttpError(int statusCode, String url) {
        String message = switch (statusCode) {
            case 400 -> "Bad request";
            case 401 -> "Authentication required";
            case 403 -> "Access forbidden";
            case 404 -> "Endpoint not found";
            case 408 -> "Request timeout";
            case 429 -> "Too many requests";
            case 500 -> "Internal server error";
            case 502 -> "Bad gateway";
            case 503 -> "Service unavailable";
            case 504 -> "Gateway timeout";
            default -> "HTTP error";
        };
        return "HTTP " + statusCode + ": " + message + " (" + url + ")";
    }

    private String formatException(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String message = cause.getMessage();

        if (message == null) {
            return "Connection failed";
        }

        // Clean up common exception messages
        if (message.contains("Connection refused")) {
            return "Connection refused";
        } else if (message.contains("Connection timed out") || message.contains("Read timed out")) {
            return "Connection timed out";
        } else if (message.contains("UnknownHostException") || message.contains("unknown host")) {
            return "Host not found";
        } else if (message.contains("SSLException") || message.contains("certificate")) {
            return "SSL/certificate error";
        } else if (message.contains("No route to host")) {
            return "Network unreachable";
        } else if (message.startsWith("HTTP ") || message.startsWith("Authentication required")
                   || message.startsWith("Endpoint not found") || message.startsWith("Access forbidden")) {
            // Already formatted by formatHttpError
            return message;
        }

        // Return cleaned message without Java class names
        return message.replaceAll("java\\.\\w+\\.\\w+Exception: ", "")
                     .replaceAll("java\\.\\w+\\.\\w+Error: ", "");
    }

    private HttpRequest buildRequest(MemberConfig config, String url, String body) {
        Duration requestTimeout = Duration.ofMillis(deltaFiPropertiesService.getDeltaFiProperties().getMemberRequestTimeout());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(requestTimeout);

        // Add authentication if configured
        if (config.credentials() != null && "basic".equals(config.credentials().type())) {
            String password = System.getenv(config.credentials().passwordEnvVar());
            if (password == null) {
                log.warn("Password env var {} not set for member {}",
                    config.credentials().passwordEnvVar(), config.name());
            } else {
                log.debug("Building request for {} with username={}, password present={}",
                    config.name(), config.credentials().username(), password != null && !password.isEmpty());
                String credentials = config.credentials().username() + ":" + password;
                String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
                builder.header("Authorization", "Basic " + encoded);
            }
        } else {
            log.debug("Building request for {} without authentication", config.name());
        }

        if (body != null) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body))
                   .header("Content-Type", "application/json");
        } else {
            builder.GET();
        }

        return builder.build();
    }

    private void storeInValkey(String memberName, MemberStatus status) {
        try {
            String json = objectMapper.writeValueAsString(status);
            String key = VALKEY_KEY_PREFIX + memberName;
            // Set with 5 minute TTL to prevent stale data accumulation
            valkeyQueue.set(key, json, Duration.ofMinutes(5));
        } catch (Exception e) {
            log.error("Failed to store member status in Valkey for member {}: {}", memberName, e.getMessage(), e);
        }
    }

    public MemberStatus loadFromValkey(String memberName) {
        try {
            String key = VALKEY_KEY_PREFIX + memberName;
            String json = valkeyQueue.getByKey(key);
            if (json == null) return null;
            return objectMapper.readValue(json, MemberStatus.class);
        } catch (Exception e) {
            log.error("Failed to load member status from Valkey for member {}: {}", memberName, e.getMessage(), e);
            return null;
        }
    }

    private List<String> getMemberNames() {
        return deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs().stream()
            .map(MemberConfig::name)
            .toList();
    }

    private MonitorResult convertToMonitorResult(SystemService.Status status) {
        try {
            // The Status object contains an ObjectNode with the MonitorResult data
            // Convert it back to MonitorResult
            return objectMapper.treeToValue(status.status(), MonitorResult.class);
        } catch (Exception e) {
            log.error("Failed to convert Status to MonitorResult", e);
            return null;
        }
    }

    /**
     * Calculate aggregated statistics across all members (including leader).
     */
    public AggregatedStats getAggregatedStats() {
        List<MemberStatus> allMembers = getAllMemberStatuses().members();

        long totalInFlight = 0;
        long totalErrors = 0;
        long totalWarmQueue = 0;
        long totalColdQueue = 0;
        long totalPaused = 0;
        int healthyCount = 0;
        int unhealthyCount = 0;

        for (MemberStatus member : allMembers) {
            if (member.inFlightCount() != null) {
                totalInFlight += member.inFlightCount();
            }
            if (member.errorCount() != null) {
                totalErrors += member.errorCount();
            }
            if (member.warmQueuedCount() != null) {
                totalWarmQueue += member.warmQueuedCount();
            }
            if (member.coldQueuedCount() != null) {
                totalColdQueue += member.coldQueuedCount();
            }
            if (member.pausedCount() != null) {
                totalPaused += member.pausedCount();
            }

            // Count healthy vs unhealthy
            if (member.connectionState() == ConnectionState.CONNECTED && isHealthy(member)) {
                healthyCount++;
            } else {
                unhealthyCount++;
            }
        }

        return new AggregatedStats(
            totalInFlight,
            totalErrors,
            totalWarmQueue,
            totalColdQueue,
            totalPaused,
            allMembers.size(),
            healthyCount,
            unhealthyCount
        );
    }

    private boolean isHealthy(MemberStatus member) {
        if (member.status() == null) return false;
        String state = member.status().state();
        return "Healthy".equalsIgnoreCase(state);
    }

    /**
     * Get flow metrics for all members in parallel, including the leader.
     * Results are cached for 5 seconds to reduce load on members.
     * Each member has its own timeout - slow members don't block the batch.
     */
    public Map<String, FlowMetrics> getAllFlowMetrics(int minutes) {
        int clampedMinutes = Math.max(1, Math.min(minutes, 1440));

        // Check cache first and clean up expired entries
        long now = System.currentTimeMillis();
        FlowMetricsCache cached = flowMetricsCache.get(clampedMinutes);
        if (cached != null && now - cached.timestamp() < FLOW_METRICS_CACHE_MAX_AGE_MS) {
            return cached.data();
        }
        
        // Clean up expired cache entries periodically
        if (flowMetricsCache.size() > 10) {
            flowMetricsCache.entrySet().removeIf(entry -> now - entry.getValue().timestamp() >= FLOW_METRICS_CACHE_MAX_AGE_MS);
        }

        Map<String, FlowMetrics> result = new ConcurrentHashMap<>();

        // Include leader's own metrics
        FlowMetrics leaderMetrics = graphiteQueryService.queryFlowMetrics(clampedMinutes);
        result.put("Leader", leaderMetrics);

        // Query all members in parallel, each with its own timeout
        List<MemberConfig> configs = deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs();
        int timeoutMs = deltaFiPropertiesService.getDeltaFiProperties().getMemberPollingTimeout();

        List<CompletableFuture<Void>> futures = configs.stream()
                .map(config -> CompletableFuture
                        .supplyAsync(() -> fetchMemberFlowMetrics(config, minutes), executor)
                        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> {
                            log.warn("Flow metrics fetch failed for {}: {}", config.name(), ex.getMessage());
                            return null; // null indicates error/unavailable
                        })
                        .thenAccept(metrics -> {
                            // Only add to result if we got a response (even if empty)
                            // null means error/unavailable - don't add to map
                            if (metrics != null) {
                                result.put(config.name(), metrics);
                            }
                        }))
                .toList();

        // Wait for all futures (they won't block each other due to individual timeouts)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Cache the result
        flowMetricsCache.put(clampedMinutes, new FlowMetricsCache(result, now));

        return result;
    }

    /**
     * Get flow metrics for a specific member.
     */
    public FlowMetrics getMemberFlowMetrics(String memberName, int minutes) {
        // Handle leader as special case
        if ("Leader".equalsIgnoreCase(memberName)) {
            int clampedMinutes = Math.max(1, Math.min(minutes, 1440));
            return graphiteQueryService.queryFlowMetrics(clampedMinutes);
        }

        MemberConfig config = deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs().stream()
                .filter(c -> c.name().equals(memberName))
                .findFirst()
                .orElse(null);

        if (config == null) {
            log.warn("Member not found: {}", memberName);
            return FlowMetrics.empty();
        }

        FlowMetrics metrics = fetchMemberFlowMetrics(config, minutes);
        return metrics != null ? metrics : FlowMetrics.empty();
    }

    private FlowMetrics fetchMemberFlowMetrics(MemberConfig config, int minutes) {
        String url = config.url() + "/api/v2/metrics/flow?minutes=" + minutes;
        try {
            HttpRequest request = buildRequest(config, url, null);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Failed to fetch flow metrics from {}: HTTP {}", config.name(), response.statusCode());
                return null;
            }

            return objectMapper.readValue(response.body(), FlowMetrics.class);
        } catch (Exception e) {
            log.error("Error fetching flow metrics from {}: {}", config.name(), e.getMessage());
            return null;
        }
    }
}
