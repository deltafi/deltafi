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
package org.deltafi.core.schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.MemberMonitorService;
import org.deltafi.core.types.leader.ConnectionState;
import org.deltafi.core.types.leader.MemberConfig;
import org.deltafi.core.types.leader.MemberStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler for polling member DeltaFi instances in leader-member deployments.
 * Polls unhealthy members more frequently than healthy members (1/6 of the configured interval).
 * Uses a bounded thread pool to handle hundreds of members without overwhelming resources.
 */
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
public class LeaderMonitorScheduler {
    private static final int MAX_CONCURRENT_POLLS = 50;
    private static final ExecutorService pollExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_POLLS);

    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final MemberMonitorService memberMonitorService;

    private final Set<String> currentlyPolling = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, OffsetDateTime> lastPollTime = new ConcurrentHashMap<>();

    /**
     * Poll members every 5 seconds.
     * Healthy members are polled every 30 seconds, unhealthy members every 5 seconds.
     * Skips members that are already being polled to prevent overlapping requests.
     * Only runs if leaderConfig is non-empty (i.e., this is a leader).
     *
     * Polling is fire-and-forget: tasks are submitted to a bounded thread pool
     * and the scheduler doesn't wait for completion. This allows efficient handling
     * of hundreds of members without blocking.
     */
    @Scheduled(fixedDelay = 5_000, initialDelay = 10_000)
    public void pollMembers() {
        List<MemberConfig> configs = deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs();

        if (configs.isEmpty()) {
            return; // Not a leader or no valid configs
        }

        OffsetDateTime now = OffsetDateTime.now();
        log.debug("Polling check for {} member sites", configs.size());

        // Submit poll tasks to executor, skipping those already being polled or not due yet
        for (MemberConfig config : configs) {
            // Check if enough time has passed since last poll
            if (!shouldPollNow(config.name(), now)) {
                log.debug("Skipping poll for {} - not due yet", config.name());
                continue;
            }

            // Check if already polling (atomic check-and-set)
            if (!currentlyPolling.add(config.name())) {
                log.debug("Skipping poll for {} - already in progress", config.name());
                continue;
            }

            // Submit poll task to executor (fire-and-forget)
            pollExecutor.submit(() -> {
                try {
                    log.debug("Polling member: {}", config.name());
                    MemberStatus result = memberMonitorService.pollMember(config);
                    lastPollTime.put(config.name(), now);

                    if (result.connectionState() == ConnectionState.CONNECTED) {
                        log.debug("Successfully polled member: {}", config.name());
                    } else {
                        log.warn("Poll completed but member {} is {}: {}",
                            config.name(), result.connectionState(), result.connectionError());
                    }
                } catch (Exception e) {
                    log.error("Failed to poll member {}: {}", config.name(), e.getMessage());
                } finally {
                    // Always remove from set when done
                    currentlyPolling.remove(config.name());
                }
            });
        }
    }

    /**
     * Determine if a member should be polled now based on its connection state.
     * Healthy members: poll at configured interval
     * Unhealthy/stale members: poll at 1/6 of configured interval
     */
    private boolean shouldPollNow(String memberName, OffsetDateTime now) {
        OffsetDateTime lastPoll = lastPollTime.get(memberName);
        if (lastPoll == null) {
            return true; // Never polled, do it now
        }

        // Get configured interval
        int healthyIntervalMs = deltaFiPropertiesService.getDeltaFiProperties().getMemberPollingInterval();
        int unhealthyIntervalMs = healthyIntervalMs / 6; // Poll unhealthy members 6x more frequently

        // Get cached status to determine connection state
        MemberStatus cachedStatus = memberMonitorService.loadFromValkey(memberName);
        long intervalMs = (cachedStatus != null && cachedStatus.connectionState() == ConnectionState.CONNECTED)
                ? healthyIntervalMs
                : unhealthyIntervalMs;

        long millisSinceLastPoll = java.time.Duration.between(lastPoll, now).toMillis();
        return millisSinceLastPoll >= intervalMs;
    }

    /**
     * Shutdown the executor service gracefully when the application stops.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down leader monitor scheduler executor");
        pollExecutor.shutdown();
        try {
            if (!pollExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Leader monitor scheduler executor did not terminate gracefully, forcing shutdown");
                pollExecutor.shutdownNow();
                if (!pollExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("Leader monitor scheduler executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while shutting down leader monitor scheduler executor", e);
            pollExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
