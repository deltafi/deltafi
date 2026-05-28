/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.generated.types.RateLimit;
import org.deltafi.core.generated.types.RateLimitUnit;
import org.deltafi.core.repo.DeltaFileFlowRepo;
import org.deltafi.core.repo.DeltaFileFlowRepoCustom;
import org.deltafi.core.repo.FlowDefinitionRepo;
import org.deltafi.common.types.DeltaFileFlowState;
import org.deltafi.core.types.DataSink;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.types.DeltaFileFlow;
import org.deltafi.core.types.FlowDefinition;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
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

    private final Set<String> rateLimitedBacklogSinks = new ConcurrentSkipListSet<>();

    @Getter
    private final ConcurrentHashMap<String, Long> allQueues = new ConcurrentHashMap<>();

    private final AtomicReference<List<WarmQueueMetrics>> cachedWarmQueueMetrics = new AtomicReference<>(List.of());
    private volatile OffsetDateTime warmQueueMetricsCacheTime = OffsetDateTime.MIN;

    private final AtomicReference<List<DeltaFileFlowRepoCustom.ColdQueueMetrics>> cachedColdQueueMetrics = new AtomicReference<>(List.of());
    private volatile OffsetDateTime coldQueueMetricsCacheTime = OffsetDateTime.MIN;

    private final AtomicReference<List<DeltaFileFlowRepoCustom.ColdQueueMetrics>> cachedRateLimitedMetrics = new AtomicReference<>(List.of());
    private volatile OffsetDateTime rateLimitedMetricsCacheTime = OffsetDateTime.MIN;

    private final AtomicReference<List<ActionExecution>> cachedRunningTasks = new AtomicReference<>(List.of());
    private volatile OffsetDateTime runningTasksCacheTime = OffsetDateTime.MIN;
    private final ConcurrentHashMap<String, OffsetDateTime> nextRateLimitedCheck = new ConcurrentHashMap<>();

    final CoreEventQueue coreEventQueue;
    final DeltaFileFlowRepo deltaFileFlowRepo;
    final FlowDefinitionRepo flowDefinitionRepo;
    final UnifiedFlowService unifiedFlowService;
    final DeltaFilesService deltaFilesService;
    final DeltaFiPropertiesService deltaFiPropertiesService;
    final Environment env;
    final RateLimitService rateLimitService;
    final DataSinkService dataSinkService;

    public QueueManagementService(CoreEventQueue coreEventQueue,
                                  DeltaFileFlowRepo deltaFileFlowRepo,
                                  FlowDefinitionRepo flowDefinitionRepo,
                                  UnifiedFlowService unifiedFlowService,
                                  @Lazy DeltaFilesService deltaFilesService,
                                  DeltaFiPropertiesService deltaFiPropertiesService,
                                  Environment env,
                                  RateLimitService rateLimitService,
                                  @Lazy DataSinkService dataSinkService) {
        this.coreEventQueue = coreEventQueue;
        this.deltaFileFlowRepo = deltaFileFlowRepo;
        this.flowDefinitionRepo = flowDefinitionRepo;
        this.unifiedFlowService = unifiedFlowService;
        this.deltaFilesService = deltaFilesService;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.env = env;
        this.rateLimitService = rateLimitService;
        this.dataSinkService = dataSinkService;
    }

    @Scheduled(fixedDelayString = "${cold.queue.refresh.duration:PT2S}")
    void refreshQueues() {
        Set<String> keys = coreEventQueue.keys();
        Set<String> actionNames = unifiedFlowService.allActionConfigurations().stream().map(ActionConfiguration::getType).collect(Collectors.toSet());
        int maxQueueSize = maxQueueSize();

        coldQueues.removeIf(q -> !keys.contains(q) || !actionNames.contains(q));
        allQueues.keySet().removeIf(q -> !keys.contains(q) || !actionNames.contains(q));

        Set<String> dbColdQueuedActions = new HashSet<>(deltaFileFlowRepo.distinctColdQueuedActions());

        Set<String> activeKeys = keys.stream().filter(actionNames::contains).collect(Collectors.toSet());
        Map<String, Long> queueSizes = coreEventQueue.sizes(activeKeys);

        queueSizes.forEach((k, size) -> {
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

    @Scheduled(fixedDelayString = "${rate.limited.cold.queue.refresh.duration:PT1S}")
    public void scheduleRateLimitedColdToWarm() {
        String scheduleMaintenance = env.getProperty("schedule.maintenance");
        if (scheduleMaintenance == null || scheduleMaintenance.equals("true")) {
            warmRateLimitedColdQueueActions();
        }
    }

    public void coldToWarm() {
        if (!checkedQueues.get()) {
            return;
        }

        // get all the actions that are currently COLD in the DB (excluding rate-limited)
        List<String> coldQueuedActions = deltaFileFlowRepo.distinctColdQueuedActions();

        // for each of these, if there is space in the warm queue, grab the oldest entries and shift to warm
        for (String queueName : coldQueuedActions) {
            if (deltaFilesService.resultQueueBackedUp()) {
                // the shared result queue is saturated; stop warming until it drains
                break;
            }
            long queueSize = allQueues.getOrDefault(queueName, 0L);
            int maxQueueSize = maxQueueSize();
            if (queueSize < maxQueueSize * 0.9) {
                deltaFilesService.requeueColdQueueActions(queueName, (int) (maxQueueSize - queueSize));
            }
        }

    }

    private void warmRateLimitedColdQueueActions() {
        if (!checkedQueues.get()) {
            return;
        }

        List<String> rateLimitedDataSinks = deltaFileFlowRepo.distinctRateLimitedColdQueuedDataSinks();
        Set<String> rateLimitedSinkSet = new HashSet<>(rateLimitedDataSinks);
        rateLimitedBacklogSinks.clear();
        rateLimitedBacklogSinks.addAll(rateLimitedSinkSet);
        nextRateLimitedCheck.keySet().removeIf(flowName -> !rateLimitedSinkSet.contains(flowName));
        OffsetDateTime now = OffsetDateTime.now();

        for (String flowName : rateLimitedDataSinks) {
            if (deltaFilesService.resultQueueBackedUp()) {
                // shared result queue is saturated; stop warming until it drains, before consuming any tokens
                break;
            }
            if (!sinkReadyForRateLimitedCheck(flowName, now)) {
                continue;
            }

            DataSink dataSink = null;
            try {
                dataSink = dataSinkService.getFlowOrThrow(flowName);
            } catch (Exception e) {
                // Data sink no longer exists - warm all entries to clear them
                nextRateLimitedCheck.remove(flowName);
                deltaFilesService.requeueRateLimitedColdQueueActions(flowName, maxQueueSize());
                continue;
            }

            if (dataSink.getRateLimit() == null) {
                // Data sink no longer has a rate limit - warm all entries
                nextRateLimitedCheck.remove(flowName);
                deltaFilesService.requeueRateLimitedColdQueueActions(flowName, maxQueueSize());
                continue;
            }

            RateLimit rateLimit = dataSink.getRateLimit();
            String bucketName = DataSinkService.dataSinkBucketName(flowName);
            long availableTokens = rateLimitService.getAvailableTokens(bucketName);

            if (availableTokens <= 0) {
                deferNextRateLimitedCheck(flowName, rateLimit, now);
                continue;
            }

            Duration period = Duration.ofSeconds(rateLimit.getDurationSeconds());

            if (rateLimit.getUnit() == RateLimitUnit.FILES) {
                // For FILES unit, tokens map 1:1 with files
                int maxFilesToWarm = (int) Math.min(availableTokens, maxQueueSize());
                if (maxFilesToWarm > 0) {
                    rateLimitService.consume(bucketName, maxFilesToWarm, rateLimit.getMaxAmount(), period);
                    deltaFilesService.requeueRateLimitedColdQueueActions(flowName, maxFilesToWarm);
                }
            } else {
                // For BYTES unit, check content sizes and allow one file to exceed the limit
                warmRateLimitedBytesFiles(flowName, bucketName, availableTokens, rateLimit.getMaxAmount(), period);
            }

            deferNextRateLimitedCheck(flowName, rateLimit, now);
        }
    }

    private boolean sinkReadyForRateLimitedCheck(String flowName, OffsetDateTime now) {
        OffsetDateTime nextCheckAt = nextRateLimitedCheck.get(flowName);
        return nextCheckAt == null || !nextCheckAt.isAfter(now);
    }

    private void deferNextRateLimitedCheck(String flowName, RateLimit rateLimit, OffsetDateTime now) {
        nextRateLimitedCheck.put(flowName, now.plus(minRateLimitedCheckInterval(rateLimit)));
    }

    private Duration minRateLimitedCheckInterval(RateLimit rateLimit) {
        long totalNanos = Duration.ofSeconds(rateLimit.getDurationSeconds()).toNanos();
        long nanosPerToken = Math.max(1L, (long) Math.ceil((double) totalNanos / rateLimit.getMaxAmount()));
        return Duration.ofNanos(nanosPerToken);
    }

    public boolean hasRateLimitedBacklog(String flowName) {
        return deltaFileFlowRepo.existsRateLimitedColdQueuedDataSink(flowName);
    }

    public void markRateLimitedBacklog(String flowName) {
        rateLimitedBacklogSinks.add(flowName);
    }

    private void warmRateLimitedBytesFiles(String flowName, String bucketName, long availableTokens,
                                           long maxAmount, Duration period) {
        List<DeltaFile> candidates = deltaFilesService.findRateLimitedColdQueuedFiles(flowName, maxQueueSize());
        if (candidates.isEmpty()) {
            return;
        }

        List<DeltaFile> filesToWarm = new ArrayList<>();
        long totalBytes = 0;
        boolean firstFile = true;

        for (DeltaFile deltaFile : candidates) {
            // Find the flow for this data sink
            Optional<DeltaFileFlow> flowOpt = deltaFile.getFlows().stream()
                    .filter(f -> f.getState() == DeltaFileFlowState.IN_FLIGHT && flowName.equals(f.getName()))
                    .findFirst();

            if (flowOpt.isEmpty()) {
                continue;
            }

            long contentSize;
            try {
                contentSize = flowOpt.get().lastContentSize();
            } catch (Exception e) {
                log.warn("Failed to get content size for DeltaFile {} flow {}, skipping", deltaFile.getDid(), flowName, e);
                continue;
            }

            // Allow the first file to exceed the limit (will drive bucket negative)
            // Subsequent files only if we have tokens remaining
            if (firstFile || totalBytes + contentSize <= availableTokens) {
                filesToWarm.add(deltaFile);
                totalBytes += contentSize;
                firstFile = false;

                // After adding first file, stop if we've exceeded available tokens
                if (totalBytes >= availableTokens) {
                    break;
                }
            } else {
                break;
            }
        }

        if (!filesToWarm.isEmpty()) {
            rateLimitService.consume(bucketName, totalBytes, maxAmount, period);
            deltaFilesService.requeueSpecificRateLimitedFiles(flowName, filesToWarm);
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
     * Results are cached for 5 seconds. Excludes rate-limited items.
     *
     * @return list of cold queue metrics per flow/action (excluding rate-limited)
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
     * Get total count of rate-limited items using cached metrics.
     *
     * @return total count of rate-limited items
     */
    public long rateLimitedCount() {
        return getCachedRateLimitedCounts().stream()
                .mapToLong(DeltaFileFlowRepoCustom.ColdQueueMetrics::count)
                .sum();
    }

    /**
     * Get rate-limited queue metrics with caching to prevent hammering PostgreSQL.
     * Results are cached for 5 seconds.
     *
     * @return list of rate-limited queue metrics per flow/action
     */
    public List<DeltaFileFlowRepoCustom.ColdQueueMetrics> getCachedRateLimitedCounts() {
        OffsetDateTime now = OffsetDateTime.now();
        if (rateLimitedMetricsCacheTime.plusSeconds(5).isAfter(now)) {
            return cachedRateLimitedMetrics.get();
        }
        List<DeltaFileFlowRepoCustom.ColdQueueMetrics> result = deltaFileFlowRepo.getRateLimitedQueueCounts();
        cachedRateLimitedMetrics.set(result);
        rateLimitedMetricsCacheTime = now;
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
