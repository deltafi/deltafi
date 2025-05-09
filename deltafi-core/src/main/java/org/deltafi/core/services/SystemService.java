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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.queue.jackey.ValkeyKeyedBlockingQueue;
import org.deltafi.core.exceptions.StorageCheckException;
import org.deltafi.core.exceptions.SystemStatusException;
import org.deltafi.core.types.DiskMetrics;
import org.deltafi.core.types.AppInfo;
import org.deltafi.core.types.AppName;
import org.deltafi.core.types.NodeMetrics;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private static final List<String> METRIC_KEYS = List.of("gauge.node.memory.usage", "gauge.node.memory.limit",
            "gauge.node.disk-minio.usage", "gauge.node.disk-minio.limit",
            "gauge.node.disk-postgres.usage", "gauge.node.disk-postgres.limit",
            "gauge.node.cpu.usage", "gauge.node.cpu.limit");

    private final PlatformService platformService;
    private final ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue;

    private Map<String, NodeMetrics> cachedNodeMetrics;
    private List<String> cachedContentNodeNames;
    private Map<String, List<AppName>> cachedAppsByNode;

    @PostConstruct
    public void init() {
        refreshSystemInfo();
        refreshNodeMetrics();
    }

    public void refreshSystemInfo() {
        this.cachedContentNodeNames = platformService.contentNodeNames();
        this.cachedAppsByNode = platformService.appsByNode();
    }

    public void refreshNodeMetrics() {
        this.cachedNodeMetrics = fetchAllNodeMetrics();
    }

    public Status systemStatus() {
        String status = Optional.ofNullable(valkeyKeyedBlockingQueue.getByKey("org.deltafi.monitor.status"))
                .orElseThrow(this::missingStatus);

        try {
            ObjectNode statusJson = MAPPER.readValue(status, ObjectNode.class);
            return new Status(statusJson);
        } catch (IOException ioException) {
            throw new SystemStatusException("Unable to parse the system status");
        }
    }

    public Versions getRunningVersions() {
        return new Versions(platformService.getRunningVersions());
    }

    public List<NodeMetrics> contentNodesMetrics() throws StorageCheckException {
        Map<String, NodeMetrics> allMetrics = getNodeMetrics();
        List<String> minioNodes = getContentNodeNames();

        if (minioNodes.isEmpty()) {
            throw new StorageCheckException("Could not find a node with content storage");
        }

        List<NodeMetrics> minioMetrics = minioNodes.stream().map(allMetrics::get).toList();

        for (NodeMetrics nodeMetric : minioMetrics) {
            if (!hasValidDiskMetric(nodeMetric)) {
                throw new StorageCheckException("Unable to get content storage metrics, received metrics " + allMetrics + ", searching for node " + nodeMetric);
            }
        }

        return minioMetrics;
    }

    public List<DiskMetrics> contentNodesDiskMetrics() throws StorageCheckException {
        List<NodeMetrics> contentMetrics = contentNodesMetrics().stream().toList();
        return contentMetrics.stream()
                .map(contentMetric -> {
                    Map<String, Long> metrics = contentMetric.resources().get("disk-minio");
                    return new DiskMetrics(contentMetric.name(), metrics.get("limit"), metrics.get("usage"));
                })
                .toList();
    }

    public Map<String, DiskMetrics> allDiskMetrics() {
        Map<String, DiskMetrics> allDiskMetrics = new HashMap<>();
        Map<String, NodeMetrics> allMetrics = getNodeMetrics();
        for (NodeMetrics nodeMetrics : allMetrics.values()) {
            DiskMetrics diskMetrics = toDiskMetrics(nodeMetrics);
            if (diskMetrics != null) {
                allDiskMetrics.put(nodeMetrics.name(), diskMetrics);
            }
        }

        return allDiskMetrics;
    }

    private DiskMetrics toDiskMetrics(NodeMetrics nodeMetrics) {
        if (hasValidDiskMetric(nodeMetrics)) {
            Map<String, Long> diskResources = nodeMetrics.resources().get("disk-minio");
            return new DiskMetrics(nodeMetrics.name(), diskResources.get("limit"), diskResources.get("usage"));
        }

        return null;
    }

    public List<NodeMetrics> nodeAppsAndMetrics() {
        Map<String, List<AppName>> appsByNode = getAppsByNode();
        Map<String, NodeMetrics> allMetrics = getNodeMetrics();

        for (Map.Entry<String, List<AppName>> apps : appsByNode.entrySet()) {
            NodeMetrics nodeMetrics = allMetrics.computeIfAbsent(apps.getKey(), NodeMetrics::new);
            nodeMetrics.addApps(apps.getValue());
        }

        return new ArrayList<>(allMetrics.values());
    }

    private boolean hasValidDiskMetric(NodeMetrics nodeMetrics) {
        if (nodeMetrics == null) {
            return false;
        }

        Map<String, Long> values = nodeMetrics.resources().get("disk-minio");
        if (values == null) {
            return false;
        }

        return values.values().stream().allMatch(this::isValid);
    }

    private boolean isValid(Long value) {
        return value != null && value > 0;
    }

    private Map<String, NodeMetrics> fetchAllNodeMetrics() {
        Map<String, Map<String, String>> allMetrics = valkeyKeyedBlockingQueue.getByKeys(METRIC_KEYS);

        Map<String, NodeMetrics> nodeMetrics = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> metricEntry : allMetrics.entrySet()) {
            String[] splitKey = metricEntry.getKey().split("\\.");
            String resourceName = splitKey[2];
            String metricName = splitKey[3];

            for (Map.Entry<String, String> valueHash : metricEntry.getValue().entrySet()) {
                String hostname = valueHash.getKey();
                NodeMetrics nodeMetric = nodeMetrics.computeIfAbsent(hostname, NodeMetrics::new);
                // contains 2 or 3 entries: value, timestamp[, partition]
                List<Object> metrics = metricList(valueHash.getValue());
                if (metrics.size() < 2 || isStale(((Number) metrics.get(1)).longValue())) {
                    continue;
                }

                nodeMetric.addMetric(resourceName, metricName, ((Number) metrics.getFirst()).longValue());
            }
        }
        return nodeMetrics;
    }

    // ignore metrics older than 60 seconds
    private boolean isStale(Long value) {
        return value == null || (System.currentTimeMillis() / 1000) - value > 60;
    }

    private List<Object> metricList(String value) {
        try {
            return MAPPER.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Bad metric data: {}", value, e);
            return List.of();
        }
    }

    private Map<String, NodeMetrics> getNodeMetrics() {
        if (cachedNodeMetrics == null) {
            cachedNodeMetrics = fetchAllNodeMetrics();
        }
        return cachedNodeMetrics;
    }

    private Map<String, List<AppName>> getAppsByNode() {
        if (cachedAppsByNode == null) {
            cachedAppsByNode = platformService.appsByNode();
        }
        return cachedAppsByNode;
    }

    public List<String> getContentNodeNames() {
        if (cachedContentNodeNames == null) {
            cachedContentNodeNames = platformService.contentNodeNames();
        }
        return this.cachedContentNodeNames;
    }

    public record Versions(List<AppInfo> versions, OffsetDateTime timestamp) {
        public Versions(List<AppInfo> versions) {
            this(versions, OffsetDateTime.now());
        }
    }

    public record Status(ObjectNode status, OffsetDateTime timestamp) {
        public Status(ObjectNode status) {
            this(status, OffsetDateTime.now());
        }
    }

    private SystemStatusException missingStatus() {
        return new SystemStatusException("Received empty response from valkey");
    }
}
