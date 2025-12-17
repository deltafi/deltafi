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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionExecution;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.repo.DeltaFileFlowRepo;
import org.deltafi.core.repo.DeltaFileFlowRepoCustom;
import org.deltafi.core.repo.FlowDefinitionRepo;
import org.deltafi.core.types.FlowDefinition;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@EnableScheduling
@Service
@Slf4j
public class QueueManagementService {
    @Getter
    private final AtomicBoolean checkedQueues = new AtomicBoolean(false);

    @Getter
    private final Set<String> coldQueues = new ConcurrentSkipListSet<>();

    @Getter
    private final ConcurrentHashMap<String, Long> allQueues = new ConcurrentHashMap<>();

    private final AtomicReference<List<WarmQueueMetrics>> cachedWarmQueueMetrics = new AtomicReference<>(List.of());
    private volatile OffsetDateTime warmQueueMetricsCacheTime = OffsetDateTime.MIN;

    private final AtomicReference<List<DeltaFileFlowRepoCustom.ColdQueueMetrics>> cachedColdQueueMetrics = new AtomicReference<>(List.of());
    private volatile OffsetDateTime coldQueueMetricsCacheTime = OffsetDateTime.MIN;

    private final AtomicReference<List<ActionExecution>> cachedRunningTasks = new AtomicReference<>(List.of());
    private volatile OffsetDateTime runningTasksCacheTime = OffsetDateTime.MIN;

    final CoreEventQueue coreEventQueue;
    final DeltaFileFlowRepo deltaFileFlowRepo;
    final FlowDefinitionRepo flowDefinitionRepo;
    final UnifiedFlowService unifiedFlowService;
    final DeltaFilesService deltaFilesService;
    final DeltaFiPropertiesService deltaFiPropertiesService;
    final Environment env;

    public QueueManagementService(CoreEventQueue coreEventQueue,
                                  DeltaFileFlowRepo deltaFileFlowRepo,
                                  FlowDefinitionRepo flowDefinitionRepo,
                                  UnifiedFlowService unifiedFlowService,
                                  @Lazy DeltaFilesService deltaFilesService,
                                  DeltaFiPropertiesService deltaFiPropertiesService,
                                  Environment env) {
        this.coreEventQueue = coreEventQueue;
        this.deltaFileFlowRepo = deltaFileFlowRepo;
        this.flowDefinitionRepo = flowDefinitionRepo;
        this.unifiedFlowService = unifiedFlowService;
        this.deltaFilesService = deltaFilesService;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.env = env;
    }

    @Scheduled(fixedDelayString = "${cold.queue.refresh.duration:PT2S}")
    void refreshQueues() {
        Set<String> keys = coreEventQueue.keys();
        Set<String> actionNames = unifiedFlowService.allActionConfigurations().stream().map(ActionConfiguration::getType).collect(Collectors.toSet());
        int maxQueueSize = maxQueueSize();

        coldQueues.removeIf(q -> !keys.contains(q) || !actionNames.contains(q));
        allQueues.keySet().removeIf(q -> !keys.contains(q) || !actionNames.contains(q));

        Set<String> dbColdQueuedActions = new HashSet<>(deltaFileFlowRepo.distinctColdQueuedActions());

        keys.stream().filter(actionNames::contains).forEach(k -> {
            long size = coreEventQueue.size(k);
            allQueues.put(k, size);
            if (coldQueues.contains(k)) {
                // only move to normal when the backlog in the database is worked off
                if (size <= maxQueueSize * 0.8 && !dbColdQueuedActions.contains(k)) {
                    coldQueues.remove(k);
                    log.info("Action queue {} has returned to normal operation.", k);
                }
            } else if (size >= maxQueueSize) {
                if (!coldQueues.contains(k)) {
                    warmToColdWarning(k, maxQueueSize);
                }
                coldQueues.add(k);
            } else if (!coldQueues.contains(k) && dbColdQueuedActions.contains(k)) {
                // handle a scenario where a large split occurs, the queue can be below the maxQueueSize as DeltaFiles are worked off but cold-queued entries exist in the database
                warmToColdWarning(k, maxQueueSize);
                coldQueues.add(k);
            }
        });

        checkedQueues.set(true);
    }

    /**
     * Checks if the specified queue name exists in the cold queues map or if the number pending
     * will move the queue to cold queue.
     *
     * @param queueName the name of the queue to check
     * @param numPending the number of pending items that have not yet been added to the queue
     * @return true if the queue exists, false otherwise
     */
    public boolean coldQueue(String queueName, long numPending) {
        return coldQueues.contains(queueName) || allQueues.getOrDefault(queueName, 0L) + numPending >= maxQueueSize();
    }

    private int maxQueueSize() {
        return deltaFiPropertiesService.getDeltaFiProperties().getInMemoryQueueSize();
    }

    @Scheduled(fixedDelay = 5000)
    public void scheduleColdToWarm() {
        // we cannot use a ConditionalOnProperty here because it is ignored by the @Scheduled annotation, check manually
        String scheduleMaintenance = env.getProperty("schedule.maintenance");
        if (scheduleMaintenance == null || scheduleMaintenance.equals("true")) {
            // split into separate method for test environments where schedule.maintenance is false
            coldToWarm();
        }
    }

    public void coldToWarm() {
        if (!checkedQueues.get()) {
            return;
        }

        // get all the actions that are currently COLD in the DB
        List<String> coldQueuedActions = deltaFileFlowRepo.distinctColdQueuedActions();

        // for each of these, if there is space in the warm queue, grab the oldest entries and shift to warm
        for (String queueName : coldQueuedActions) {
            long queueSize = allQueues.getOrDefault(queueName, 0L);
            int maxQueueSize = maxQueueSize();
            if (queueSize < maxQueueSize * 0.9) {
                deltaFilesService.requeueColdQueueActions(queueName, (int) (maxQueueSize - queueSize));
            }
        }
    }

    public Set<String> coldQueueActions() {
        return unifiedFlowService.allActionConfigurations().stream()
                .filter(a -> coldQueues.contains(a.getType()))
                .map(ActionConfiguration::getName)
                .collect(Collectors.toSet());
    }

    private void warmToColdWarning(String queueName, long maxQueueSize) {
        log.warn("Action queue {} exceeded the maximum size of {}. Future events will be placed in a cold queue on disk until the queue is relieved.", queueName, maxQueueSize);
    }

    /**
     * Get detailed warm queue metrics aggregated by (actionClass, flowName, actionName).
     * Uses streaming aggregation to avoid loading all queue items into memory.
     * Results are cached for a few seconds to avoid excessive Valkey scanning.
     *
     * @return list of warm queue metrics per flow/action
     */
    public List<WarmQueueMetrics> getDetailedWarmQueueMetrics() {
        OffsetDateTime now = OffsetDateTime.now();
        // Cache for 5 seconds
        if (warmQueueMetricsCacheTime.plusSeconds(5).isAfter(now)) {
            return cachedWarmQueueMetrics.get();
        }

        // Build flow name to type lookup map
        Map<String, String> flowTypeMap = flowDefinitionRepo.findAll().stream()
                .collect(Collectors.toMap(FlowDefinition::getName, fd -> fd.getType().name(), (a, b) -> a));

        Map<WarmQueueKey, WarmQueueAggregator> aggregation = new HashMap<>();

        // Stream through each queue using cursor-based iteration
        // Only the aggregation map stays in memory, not all queue items
        for (String actionClass : allQueues.keySet()) {
            coreEventQueue.streamQueue(actionClass, item -> {
                WarmQueueKey key = new WarmQueueKey(actionClass, item.flowName(), item.actionName());
                aggregation.computeIfAbsent(key, k -> new WarmQueueAggregator()).add(item.queuedAt(), item.did());
            });
        }

        List<WarmQueueMetrics> result = aggregation.entrySet().stream()
                .map(e -> new WarmQueueMetrics(
                        e.getKey().actionClass(),
                        e.getKey().flowName(),
                        flowTypeMap.getOrDefault(e.getKey().flowName(), "UNKNOWN"),
                        e.getKey().actionName(),
                        e.getValue().count,
                        e.getValue().oldest,
                        e.getValue().oldestDid))
                .sorted(Comparator.comparing(WarmQueueMetrics::actionClass)
                        .thenComparing(WarmQueueMetrics::flowName)
                        .thenComparing(WarmQueueMetrics::actionName))
                .toList();

        cachedWarmQueueMetrics.set(result);
        warmQueueMetricsCacheTime = now;
        return result;
    }

    private record WarmQueueKey(String actionClass, String flowName, String actionName) {}

    private static class WarmQueueAggregator {
        int count = 0;
        OffsetDateTime oldest = null;
        UUID oldestDid = null;

        void add(OffsetDateTime queuedAt, UUID did) {
            count++;
            if (oldest == null || queuedAt.isBefore(oldest)) {
                oldest = queuedAt;
                oldestDid = did;
            }
        }
    }

    /**
     * Warm queue metrics aggregated by action class, flow name, and action name.
     */
    public record WarmQueueMetrics(
            String actionClass,
            String flowName,
            String flowType,
            String actionName,
            int count,
            OffsetDateTime oldestQueuedAt,
            UUID oldestDid) {}

    /**
     * Get total count of items in cold queues using cached metrics.
     *
     * @return total count of cold queued items
     */
    public long coldQueuedCount() {
        return getCachedColdQueueCounts().stream()
                .mapToLong(DeltaFileFlowRepoCustom.ColdQueueMetrics::count)
                .sum();
    }

    /**
     * Get cold queued counts grouped by action class using cached metrics.
     *
     * @return map of action class to count
     */
    public Map<String, Integer> coldQueuedActionsCount() {
        return getCachedColdQueueCounts().stream()
                .collect(Collectors.groupingBy(
                        DeltaFileFlowRepoCustom.ColdQueueMetrics::actionClass,
                        Collectors.summingInt(DeltaFileFlowRepoCustom.ColdQueueMetrics::count)));
    }

    /**
     * Get distinct action classes that have cold queued items using cached metrics.
     *
     * @return set of action class names
     */
    public Set<String> distinctColdQueuedActions() {
        return getCachedColdQueueCounts().stream()
                .map(DeltaFileFlowRepoCustom.ColdQueueMetrics::actionClass)
                .collect(Collectors.toSet());
    }

    /**
     * Get cold queue metrics with caching to prevent hammering PostgreSQL.
     * Results are cached for 5 seconds.
     *
     * @return list of cold queue metrics per flow/action
     */
    public List<DeltaFileFlowRepoCustom.ColdQueueMetrics> getCachedColdQueueCounts() {
        OffsetDateTime now = OffsetDateTime.now();
        if (coldQueueMetricsCacheTime.plusSeconds(5).isAfter(now)) {
            return cachedColdQueueMetrics.get();
        }
        List<DeltaFileFlowRepoCustom.ColdQueueMetrics> result = deltaFileFlowRepo.getColdQueueCounts();
        cachedColdQueueMetrics.set(result);
        coldQueueMetricsCacheTime = now;
        return result;
    }

    /**
     * Get running tasks with caching to prevent hammering Valkey.
     * Results are cached for 5 seconds.
     *
     * @return list of currently running action executions
     */
    public List<ActionExecution> getCachedRunningTasks() {
        OffsetDateTime now = OffsetDateTime.now();
        if (runningTasksCacheTime.plusSeconds(5).isAfter(now)) {
            return cachedRunningTasks.get();
        }
        List<ActionExecution> result = coreEventQueue.getLongRunningTasks();
        cachedRunningTasks.set(result);
        runningTasksCacheTime = now;
        return result;
    }

    /**
     * Get the oldest queued info (timestamp and DID) across all warm and cold queues.
     * Uses cached metrics for warm queue and queries for cold queue DID when needed.
     *
     * @return the oldest queued info, or null if no items are queued
     */
    public OldestQueuedInfo getOldestQueuedInfo() {
        OffsetDateTime warmOldest = null;
        UUID warmOldestDid = null;

        for (WarmQueueMetrics m : getDetailedWarmQueueMetrics()) {
            if (m.oldestQueuedAt() != null && (warmOldest == null || m.oldestQueuedAt().isBefore(warmOldest))) {
                warmOldest = m.oldestQueuedAt();
                warmOldestDid = m.oldestDid();
            }
        }

        var coldEntry = deltaFileFlowRepo.getOldestColdQueueEntry();
        OffsetDateTime coldOldest = coldEntry.map(DeltaFileFlowRepoCustom.OldestColdQueueEntry::queuedAt).orElse(null);
        UUID coldOldestDid = coldEntry.map(DeltaFileFlowRepoCustom.OldestColdQueueEntry::did).orElse(null);

        if (warmOldest == null && coldOldest == null) {
            return null;
        }

        if (warmOldest == null) {
            return new OldestQueuedInfo(coldOldest, coldOldestDid);
        }
        if (coldOldest == null) {
            return new OldestQueuedInfo(warmOldest, warmOldestDid);
        }

        return warmOldest.isBefore(coldOldest)
                ? new OldestQueuedInfo(warmOldest, warmOldestDid)
                : new OldestQueuedInfo(coldOldest, coldOldestDid);
    }

    /**
     * Info about the oldest queued item.
     */
    public record OldestQueuedInfo(OffsetDateTime timestamp, UUID did) {}
}
