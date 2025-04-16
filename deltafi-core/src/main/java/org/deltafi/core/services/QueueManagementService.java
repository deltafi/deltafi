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
import org.deltafi.core.repo.DeltaFileFlowRepo;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
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

    final CoreEventQueue coreEventQueue;
    final DeltaFileFlowRepo deltaFileFlowRepo;
    final UnifiedFlowService unifiedFlowService;
    final DeltaFilesService deltaFilesService;
    final DeltaFiPropertiesService deltaFiPropertiesService;
    final Environment env;

    public QueueManagementService(CoreEventQueue coreEventQueue,
                                  DeltaFileFlowRepo deltaFileFlowRepo,
                                  UnifiedFlowService unifiedFlowService,
                                  @Lazy DeltaFilesService deltaFilesService,
                                  DeltaFiPropertiesService deltaFiPropertiesService,
                                  Environment env) {
        this.coreEventQueue = coreEventQueue;
        this.deltaFileFlowRepo = deltaFileFlowRepo;
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

        keys.stream().filter(actionNames::contains).forEach(k -> {
            long size = coreEventQueue.size(k);
            allQueues.put(k, size);
            if (coldQueues.contains(k)) {
                // only move to normal when the backlog in the database is worked off
                if (size <= maxQueueSize * 0.8 && !deltaFileFlowRepo.isColdQueued(k)) {
                    coldQueues.remove(k);
                    log.info("Action queue {} has returned to normal operation.", k);
                }
            } else if (size >= maxQueueSize) {
                if (!coldQueues.contains(k)) {
                    warmToColdWarning(k, maxQueueSize);
                }
                coldQueues.add(k);
            } else if (!coldQueues.contains(k) && deltaFileFlowRepo.isColdQueued(k)) {
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
}
