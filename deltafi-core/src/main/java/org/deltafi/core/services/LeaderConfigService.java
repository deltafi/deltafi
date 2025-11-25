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
// ABOUTME: Service for leader configuration operations including snapshot fetching and diff computation.
// ABOUTME: Enables comparing configurations across leader and member instances.
package org.deltafi.core.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.KeyValue;
import org.deltafi.core.types.leader.*;
import org.deltafi.core.types.snapshot.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
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
 * Service for leader configuration management operations.
 * Handles fetching member configurations and computing diffs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderConfigService {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Duration SNAPSHOT_CACHE_DURATION = Duration.ofSeconds(5);

    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final SystemSnapshotService systemSnapshotService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Cache for snapshots to avoid hammering on page reloads
    private record SnapshotCache(Snapshot snapshot, long timestamp) {}
    private final ConcurrentHashMap<String, SnapshotCache> snapshotCache = new ConcurrentHashMap<>();
    private static final long SNAPSHOT_CACHE_MAX_AGE_MS = SNAPSHOT_CACHE_DURATION.toMillis();

    /**
     * Get plugins installed on all members.
     *
     * @return Response containing plugins and unreachable members
     */
    public PluginsResponse getAllMemberPlugins() {
        Map<String, List<PluginInfo>> result = new ConcurrentHashMap<>();
        List<String> membersNotReporting = Collections.synchronizedList(new ArrayList<>());

        // Include leader's plugins
        Snapshot leaderSnapshot = systemSnapshotService.assembleCurrentSnapshot();
        result.put("Leader", extractPlugins(leaderSnapshot));

        // Fetch from all members in parallel
        List<MemberConfig> configs = deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs();
        int timeoutMs = deltaFiPropertiesService.getDeltaFiProperties().getMemberPollingTimeout();

        List<CompletableFuture<Void>> futures = configs.stream()
                .map(config -> CompletableFuture
                        .supplyAsync(() -> fetchMemberSnapshot(config), executor)
                        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> {
                            log.warn("Failed to fetch snapshot from {}: {}", config.name(), ex.getMessage());
                            return null;
                        })
                        .thenAccept(snapshot -> {
                            if (snapshot != null) {
                                result.put(config.name(), extractPlugins(snapshot));
                            } else {
                                membersNotReporting.add(config.name());
                            }
                        }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return new PluginsResponse(result, membersNotReporting);
    }

    /**
     * Get the current snapshot for a member (or leader).
     * Results are cached for 5 seconds to avoid excessive load on page reloads.
     *
     * @param memberName The member name, or "Leader" for the leader's snapshot
     * @return The snapshot, or null if not available
     */
    public Snapshot getMemberSnapshot(String memberName) {
        String cacheKey = memberName.toLowerCase();
        long now = System.currentTimeMillis();

        // Check cache and clean up expired entries
        SnapshotCache cached = snapshotCache.get(cacheKey);
        if (cached != null && (now - cached.timestamp()) < SNAPSHOT_CACHE_MAX_AGE_MS) {
            return cached.snapshot();
        }
        
        // Clean up expired cache entries periodically
        if (snapshotCache.size() > 20) {
            snapshotCache.entrySet().removeIf(entry -> now - entry.getValue().timestamp() >= SNAPSHOT_CACHE_MAX_AGE_MS);
        }

        Snapshot snapshot;
        if ("Leader".equalsIgnoreCase(memberName)) {
            snapshot = systemSnapshotService.assembleCurrentSnapshot();
        } else {
            MemberConfig config = findMemberConfig(memberName);
            if (config == null) {
                log.warn("Member not found: {}", memberName);
                return null;
            }
            snapshot = fetchMemberSnapshot(config);
        }

        // Cache the result (even null results to avoid repeated failed lookups)
        if (snapshot != null) {
            snapshotCache.put(cacheKey, new SnapshotCache(snapshot, now));
        }

        return snapshot;
    }

    /**
     * Compute the configuration diff between leader and a member.
     *
     * @param memberName The member to compare against
     * @return The configuration diff, or null if member not found
     */
    public ConfigDiff computeDiff(String memberName) {
        Snapshot leaderSnapshot = systemSnapshotService.assembleCurrentSnapshot();
        Snapshot memberSnapshot = getMemberSnapshot(memberName);

        if (memberSnapshot == null) {
            return null;
        }

        List<DiffSection> sections = new ArrayList<>();

        // Compare each section
        sections.add(comparePlugins(leaderSnapshot.getPlugins(), memberSnapshot.getPlugins()));
        sections.add(compareDataSinks(leaderSnapshot.getDataSinks(), memberSnapshot.getDataSinks()));
        sections.add(compareTransformFlows(leaderSnapshot.getTransformFlows(), memberSnapshot.getTransformFlows()));
        sections.add(compareRestDataSources(leaderSnapshot.getRestDataSources(), memberSnapshot.getRestDataSources()));
        sections.add(compareTimedDataSources(leaderSnapshot.getTimedDataSources(), memberSnapshot.getTimedDataSources()));
        sections.add(compareDeltaFiProperties(leaderSnapshot.getDeltaFiProperties(), memberSnapshot.getDeltaFiProperties()));

        // Filter out empty sections
        sections = sections.stream()
                .filter(s -> s.diffCount() > 0)
                .toList();

        return new ConfigDiff("Leader", memberName, OffsetDateTime.now(), sections);
    }

    /**
     * Compute diff between two snapshots directly. Package-private for testing.
     */
    ConfigDiff computeDiffForTest(Snapshot leaderSnapshot, Snapshot memberSnapshot) {
        List<DiffSection> sections = new ArrayList<>();

        sections.add(comparePlugins(leaderSnapshot.getPlugins(), memberSnapshot.getPlugins()));
        sections.add(compareDataSinks(leaderSnapshot.getDataSinks(), memberSnapshot.getDataSinks()));
        sections.add(compareTransformFlows(leaderSnapshot.getTransformFlows(), memberSnapshot.getTransformFlows()));
        sections.add(compareRestDataSources(leaderSnapshot.getRestDataSources(), memberSnapshot.getRestDataSources()));
        sections.add(compareTimedDataSources(leaderSnapshot.getTimedDataSources(), memberSnapshot.getTimedDataSources()));
        sections.add(compareDeltaFiProperties(leaderSnapshot.getDeltaFiProperties(), memberSnapshot.getDeltaFiProperties()));

        sections = sections.stream()
                .filter(s -> s.diffCount() > 0)
                .toList();

        return new ConfigDiff("Leader", "TestMember", OffsetDateTime.now(), sections);
    }

    private Snapshot fetchMemberSnapshot(MemberConfig config) {
        String url = config.url() + "/api/v2/system/snapshot/current";
        try {
            HttpRequest request = buildRequest(config, url);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Failed to fetch snapshot from {}: HTTP {}", config.name(), response.statusCode());
                return null;
            }

            return objectMapper.readValue(response.body(), Snapshot.class);
        } catch (Exception e) {
            log.error("Error fetching snapshot from {}: {}", config.name(), e.getMessage());
            return null;
        }
    }

    private HttpRequest buildRequest(MemberConfig config, String url) {
        Duration timeout = Duration.ofMillis(deltaFiPropertiesService.getDeltaFiProperties().getMemberRequestTimeout());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET();

        if (config.credentials() != null && "basic".equals(config.credentials().type())) {
            String password = System.getenv(config.credentials().passwordEnvVar());
            if (password != null) {
                String credentials = config.credentials().username() + ":" + password;
                String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
                builder.header("Authorization", "Basic " + encoded);
            }
        }

        return builder.build();
    }

    private MemberConfig findMemberConfig(String memberName) {
        return deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs().stream()
                .filter(c -> c.name().equals(memberName))
                .findFirst()
                .orElse(null);
    }

    private List<PluginInfo> extractPlugins(Snapshot snapshot) {
        if (snapshot.getPlugins() == null) {
            return List.of();
        }
        return snapshot.getPlugins().stream()
                .map(p -> new PluginInfo(
                        p.pluginCoordinates().getGroupId(),
                        p.pluginCoordinates().getArtifactId(),
                        p.pluginCoordinates().getVersion(),
                        p.pluginCoordinates().getArtifactId(), // displayName fallback
                        p.imageName(),
                        extractImageTag(p.imageName())
                ))
                .toList();
    }

    private String extractImageTag(String imageName) {
        if (imageName == null) return null;
        int colonIdx = imageName.lastIndexOf(':');
        return colonIdx > 0 ? imageName.substring(colonIdx + 1) : "latest";
    }

    // Comparison methods for each section

    private DiffSection comparePlugins(List<PluginSnapshot> leader, List<PluginSnapshot> member) {
        List<DiffItem> diffs = new ArrayList<>();

        Map<String, PluginSnapshot> leaderMap = toPluginMap(leader);
        Map<String, PluginSnapshot> memberMap = toPluginMap(member);

        // Find removed (in leader but not member)
        for (var entry : leaderMap.entrySet()) {
            if (!memberMap.containsKey(entry.getKey())) {
                diffs.add(new DiffItem(
                        "plugins." + entry.getKey(),
                        DiffType.REMOVED,
                        entry.getValue().pluginCoordinates().getVersion(),
                        null
                ));
            }
        }

        // Find added (in member but not leader)
        for (var entry : memberMap.entrySet()) {
            if (!leaderMap.containsKey(entry.getKey())) {
                diffs.add(new DiffItem(
                        "plugins." + entry.getKey(),
                        DiffType.ADDED,
                        null,
                        entry.getValue().pluginCoordinates().getVersion()
                ));
            }
        }

        // Find modified (different versions)
        for (var entry : leaderMap.entrySet()) {
            PluginSnapshot memberPlugin = memberMap.get(entry.getKey());
            if (memberPlugin != null) {
                String leaderVersion = entry.getValue().pluginCoordinates().getVersion();
                String memberVersion = memberPlugin.pluginCoordinates().getVersion();
                if (!Objects.equals(leaderVersion, memberVersion)) {
                    diffs.add(new DiffItem(
                            "plugins." + entry.getKey() + ".version",
                            DiffType.MODIFIED,
                            leaderVersion,
                            memberVersion
                    ));
                }
            }
        }

        return new DiffSection("plugins", diffs);
    }

    private Map<String, PluginSnapshot> toPluginMap(List<PluginSnapshot> plugins) {
        if (plugins == null) return Map.of();
        return plugins.stream()
                .collect(Collectors.toMap(
                        p -> p.pluginCoordinates().getGroupId() + ":" + p.pluginCoordinates().getArtifactId(),
                        p -> p
                ));
    }

    private DiffSection compareDataSinks(List<DataSinkSnapshot> leader, List<DataSinkSnapshot> member) {
        List<DiffItem> diffs = new ArrayList<>();

        Map<String, DataSinkSnapshot> leaderMap = toDataSinkMap(leader);
        Map<String, DataSinkSnapshot> memberMap = toDataSinkMap(member);

        // Find removed/added
        for (String name : leaderMap.keySet()) {
            if (!memberMap.containsKey(name)) {
                diffs.add(new DiffItem("dataSinks." + name, DiffType.REMOVED, name, null));
            }
        }
        for (String name : memberMap.keySet()) {
            if (!leaderMap.containsKey(name)) {
                diffs.add(new DiffItem("dataSinks." + name, DiffType.ADDED, null, name));
            }
        }

        // Compare matching data sinks
        for (var entry : leaderMap.entrySet()) {
            DataSinkSnapshot memberSink = memberMap.get(entry.getKey());
            if (memberSink != null) {
                compareFlowSnapshot(entry.getValue(), memberSink, "dataSinks." + entry.getKey(), diffs);
            }
        }

        return new DiffSection("dataSinks", diffs);
    }

    private Map<String, DataSinkSnapshot> toDataSinkMap(List<DataSinkSnapshot> sinks) {
        if (sinks == null) return Map.of();
        return sinks.stream().collect(Collectors.toMap(DataSinkSnapshot::getName, s -> s));
    }

    private DiffSection compareTransformFlows(List<TransformFlowSnapshot> leader, List<TransformFlowSnapshot> member) {
        List<DiffItem> diffs = new ArrayList<>();

        Map<String, TransformFlowSnapshot> leaderMap = toTransformFlowMap(leader);
        Map<String, TransformFlowSnapshot> memberMap = toTransformFlowMap(member);

        for (String name : leaderMap.keySet()) {
            if (!memberMap.containsKey(name)) {
                diffs.add(new DiffItem("transformFlows." + name, DiffType.REMOVED, name, null));
            }
        }
        for (String name : memberMap.keySet()) {
            if (!leaderMap.containsKey(name)) {
                diffs.add(new DiffItem("transformFlows." + name, DiffType.ADDED, null, name));
            }
        }

        for (var entry : leaderMap.entrySet()) {
            TransformFlowSnapshot memberFlow = memberMap.get(entry.getKey());
            if (memberFlow != null) {
                compareFlowSnapshot(entry.getValue(), memberFlow, "transformFlows." + entry.getKey(), diffs);
            }
        }

        return new DiffSection("transformFlows", diffs);
    }

    private Map<String, TransformFlowSnapshot> toTransformFlowMap(List<TransformFlowSnapshot> flows) {
        if (flows == null) return Map.of();
        return flows.stream().collect(Collectors.toMap(TransformFlowSnapshot::getName, f -> f));
    }

    private DiffSection compareRestDataSources(List<RestDataSourceSnapshot> leader, List<RestDataSourceSnapshot> member) {
        List<DiffItem> diffs = new ArrayList<>();

        Map<String, RestDataSourceSnapshot> leaderMap = toRestDataSourceMap(leader);
        Map<String, RestDataSourceSnapshot> memberMap = toRestDataSourceMap(member);

        for (String name : leaderMap.keySet()) {
            if (!memberMap.containsKey(name)) {
                diffs.add(new DiffItem("restDataSources." + name, DiffType.REMOVED, name, null));
            }
        }
        for (String name : memberMap.keySet()) {
            if (!leaderMap.containsKey(name)) {
                diffs.add(new DiffItem("restDataSources." + name, DiffType.ADDED, null, name));
            }
        }

        for (var entry : leaderMap.entrySet()) {
            RestDataSourceSnapshot memberSource = memberMap.get(entry.getKey());
            if (memberSource != null) {
                compareDataSourceSnapshot(entry.getValue(), memberSource, "restDataSources." + entry.getKey(), diffs);
            }
        }

        return new DiffSection("restDataSources", diffs);
    }

    private Map<String, RestDataSourceSnapshot> toRestDataSourceMap(List<RestDataSourceSnapshot> sources) {
        if (sources == null) return Map.of();
        return sources.stream().collect(Collectors.toMap(RestDataSourceSnapshot::getName, s -> s));
    }

    private DiffSection compareTimedDataSources(List<TimedDataSourceSnapshot> leader, List<TimedDataSourceSnapshot> member) {
        List<DiffItem> diffs = new ArrayList<>();

        Map<String, TimedDataSourceSnapshot> leaderMap = toTimedDataSourceMap(leader);
        Map<String, TimedDataSourceSnapshot> memberMap = toTimedDataSourceMap(member);

        for (String name : leaderMap.keySet()) {
            if (!memberMap.containsKey(name)) {
                diffs.add(new DiffItem("timedDataSources." + name, DiffType.REMOVED, name, null));
            }
        }
        for (String name : memberMap.keySet()) {
            if (!leaderMap.containsKey(name)) {
                diffs.add(new DiffItem("timedDataSources." + name, DiffType.ADDED, null, name));
            }
        }

        for (var entry : leaderMap.entrySet()) {
            TimedDataSourceSnapshot memberSource = memberMap.get(entry.getKey());
            if (memberSource != null) {
                compareDataSourceSnapshot(entry.getValue(), memberSource, "timedDataSources." + entry.getKey(), diffs);
            }
        }

        return new DiffSection("timedDataSources", diffs);
    }

    /**
     * Shutdown the executor service gracefully when the application stops.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down leader config service executor");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Leader config service executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("Leader config service executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while shutting down leader config service executor", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, TimedDataSourceSnapshot> toTimedDataSourceMap(List<TimedDataSourceSnapshot> sources) {
        if (sources == null) return Map.of();
        return sources.stream().collect(Collectors.toMap(TimedDataSourceSnapshot::getName, s -> s));
    }

    private DiffSection compareDeltaFiProperties(List<KeyValue> leader, List<KeyValue> member) {
        List<DiffItem> diffs = new ArrayList<>();

        Map<String, String> leaderMap = toKeyValueMap(leader);
        Map<String, String> memberMap = toKeyValueMap(member);

        for (var entry : leaderMap.entrySet()) {
            String memberValue = memberMap.get(entry.getKey());
            if (memberValue == null) {
                diffs.add(new DiffItem("properties." + entry.getKey(), DiffType.REMOVED, entry.getValue(), null));
            } else if (!Objects.equals(entry.getValue(), memberValue)) {
                diffs.add(new DiffItem("properties." + entry.getKey(), DiffType.MODIFIED, entry.getValue(), memberValue));
            }
        }

        for (var entry : memberMap.entrySet()) {
            if (!leaderMap.containsKey(entry.getKey())) {
                diffs.add(new DiffItem("properties." + entry.getKey(), DiffType.ADDED, null, entry.getValue()));
            }
        }

        return new DiffSection("deltaFiProperties", diffs);
    }

    private Map<String, String> toKeyValueMap(List<KeyValue> keyValues) {
        if (keyValues == null) return Map.of();
        return keyValues.stream().collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
    }

    // Helper methods for comparing flow/data source snapshots

    private void compareFlowSnapshot(FlowSnapshot leader, FlowSnapshot member, String prefix, List<DiffItem> diffs) {
        if (!Objects.equals(leader.isRunning(), member.isRunning())) {
            diffs.add(new DiffItem(prefix + ".running", DiffType.MODIFIED, leader.isRunning(), member.isRunning()));
        }
        if (!Objects.equals(leader.isTestMode(), member.isTestMode())) {
            diffs.add(new DiffItem(prefix + ".testMode", DiffType.MODIFIED, leader.isTestMode(), member.isTestMode()));
        }
    }

    private void compareDataSourceSnapshot(DataSourceSnapshot leader, DataSourceSnapshot member, String prefix, List<DiffItem> diffs) {
        if (!Objects.equals(leader.isRunning(), member.isRunning())) {
            diffs.add(new DiffItem(prefix + ".running", DiffType.MODIFIED, leader.isRunning(), member.isRunning()));
        }
        if (!Objects.equals(leader.isTestMode(), member.isTestMode())) {
            diffs.add(new DiffItem(prefix + ".testMode", DiffType.MODIFIED, leader.isTestMode(), member.isTestMode()));
        }
        if (!Objects.equals(leader.getMaxErrors(), member.getMaxErrors())) {
            diffs.add(new DiffItem(prefix + ".maxErrors", DiffType.MODIFIED, leader.getMaxErrors(), member.getMaxErrors()));
        }
    }
}
