/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.DeltaFiConfiguration;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.types.ColdQueuedActionSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@EnableScheduling
@Service
@Slf4j
public class QueueManagementService {
    @Getter
    private final AtomicBoolean checkedQueues = new AtomicBoolean(false);

    @Getter
    private final ConcurrentHashMap<String, Long> coldQueues = new ConcurrentHashMap<>();

    ActionDescriptorService actionDescriptorService;
    ActionEventQueue actionEventQueue;
    DeltaFileRepo deltaFileRepo;
    UnifiedFlowService unifiedFlowService;
    DeltaFilesService deltaFilesService;
    DeltaFiPropertiesService deltaFiPropertiesService;

    public QueueManagementService(ActionDescriptorService actionDescriptorService,
                                  ActionEventQueue actionEventQueue,
                                  DeltaFileRepo deltaFileRepo,
                                  UnifiedFlowService unifiedFlowService,
                                  @Lazy DeltaFilesService deltaFilesService,
                                  DeltaFiPropertiesService deltaFiPropertiesService) {
        this.actionDescriptorService = actionDescriptorService;
        this.actionEventQueue = actionEventQueue;
        this.deltaFileRepo = deltaFileRepo;
        this.unifiedFlowService = unifiedFlowService;
        this.deltaFilesService = deltaFilesService;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
    }

    @Scheduled(fixedDelay = 2000)
    void identifyColdQueues() {
        Set<String> keys = actionEventQueue.keys();
        List<String> actionNames = actionDescriptorService.getAll().stream().map(ActionDescriptor::getName).toList();
        int maxQueueSize = maxQueueSize();

        coldQueues.keySet().removeIf(q -> !keys.contains(q) || !actionNames.contains(q));

        keys.stream().filter(actionNames::contains).forEach(k -> {
            long size = actionEventQueue.size(k);
            if (coldQueues.containsKey(k)) {
                if (size <= maxQueueSize * 0.8) {
                    coldQueues.remove(k);
                    log.info("Action queue " + k + " has returned to normal operation.");
                } else {
                    coldQueues.put(k, size);
                }
            } else if (size >= maxQueueSize) {
                if (!coldQueues.containsKey(k)) {
                    log.warn("Action queue " + k + " exceeded the maximum size of " + maxQueueSize + ". Future events will be placed in a cold queue on disk until the queue is relieved.");
                }
                coldQueues.put(k, size);
            }
        });

        checkedQueues.set(true);
    }

    /**
     * Checks if the specified queue name exists in the cold queues map.
     *
     * @param queueName the name of the queue to check
     * @return true if the queue exists, false otherwise
     */
    public boolean coldQueue(String queueName) {
        return coldQueues.containsKey(queueName);
    }

    private int maxQueueSize() {
        return deltaFiPropertiesService.getDeltaFiProperties().getInMemoryQueueSize();
    }

    @Scheduled(fixedDelay = 2000)
    @ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
    public void coldToWarm() {
        if (!checkedQueues.get()) {
            return;
        }

        // get all the actions that are currently COLD in the DB
        List<ColdQueuedActionSummary> coldQueuedActionSummaries = deltaFileRepo.coldQueuedActionsSummary();
        Map<String, List<String>> coldQueueActions = new HashMap<>();
        for (ColdQueuedActionSummary coldQueuedActionSummary : coldQueuedActionSummaries) {
            ActionConfiguration coldQueuedActionConfig = unifiedFlowService.runningAction(coldQueuedActionSummary.getActionName(),
                    coldQueuedActionSummary.getActionType());

            if (coldQueuedActionConfig != null) {
                String queueName = coldQueuedActionConfig.getType();
                coldQueueActions.computeIfAbsent(queueName, k -> new ArrayList<>()).add(coldQueuedActionSummary.getActionName());
            }
        }

        // for each of these, if there is space in the warm queue, grab the oldest entries and shift to warm
        for (String queueName : coldQueueActions.keySet()) {
            long queueSize = coldQueues.getOrDefault(queueName, 0L);
            int maxQueueSize = maxQueueSize();
            if (queueSize < maxQueueSize * 0.9) {
                deltaFilesService.requeueColdQueueActions(coldQueueActions.get(queueName), (int) (maxQueueSize - queueSize));
            }
        }
    }

    public Set<String> coldQueueActions() {
        return unifiedFlowService.allActionConfigurations().stream()
                .filter(a -> coldQueues.containsKey(a.getType()))
                .map(DeltaFiConfiguration::getName)
                .collect(Collectors.toSet());
    }
}