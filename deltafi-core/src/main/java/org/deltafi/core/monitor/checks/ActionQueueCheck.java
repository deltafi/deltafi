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
package org.deltafi.core.monitor.checks;

import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.deltafi.core.services.DeltaFiPropertiesService;

import java.util.*;
import java.util.Map.Entry;

import static org.deltafi.core.monitor.checks.CheckResult.CODE_YELLOW;

@MonitorProfile
public class ActionQueueCheck extends StatusCheck {

    private static final Set<String> IGNORED_QUEUE_NAMES = Set.of(
            ValkeyKeyedBlockingQueue.MONITOR_STATUS_HASH,
            ValkeyKeyedBlockingQueue.HEARTBEAT_HASH,
            ValkeyKeyedBlockingQueue.LONG_RUNNING_TASKS_HASH
    );

    private final ValkeyKeyedBlockingQueue valkeyQueue;
    private final MetricService metricService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;

    public ActionQueueCheck(ValkeyKeyedBlockingQueue valkeyQueue, MetricService metricService, DeltaFiPropertiesService deltaFiPropertiesService) {
        super("Action Queue Check");
        this.valkeyQueue = valkeyQueue;
        this.metricService = metricService;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
    }

    @Override
    public CheckResult check() {
        ResultBuilder resultBuilder = new ResultBuilder();
        int threshold = deltaFiPropertiesService.getDeltaFiProperties().getCheckActionQueueSizeThreshold();

        Set<String> recentQueueNames = valkeyQueue.getRecentQueues();
        Map<String, Long> queuesOverThreshold = checkQueueSizes(recentQueueNames, threshold);
        Map<String, Long> orphans = findOrphans(recentQueueNames);

        addOverThresholdMessage(queuesOverThreshold, resultBuilder, threshold);
        addOrphanMessage(orphans, resultBuilder);

        return result(resultBuilder);
    }

    private Map<String, Long> checkQueueSizes(Set<String> recentQueueNames, int threshold) {
        Map<String, Long> queuesCounts = valkeyQueue.queuesCounts(recentQueueNames);
        Map<String, Long> fatQueues = new HashMap<>();

        generateQueueSizeMetrics(queuesCounts);

        for (Entry<String, Long> entry : queuesCounts.entrySet()) {
            long size = entry.getValue() != null ? entry.getValue() : -1;
            if (size > threshold) {
                fatQueues.put(entry.getKey(), size);
            }
        }
        return fatQueues;
    }

    private Map<String, Long> findOrphans(Set<String> recentQueueNames) {
        Set<String> allKeys = valkeyQueue.keys();
        Set<String> orphans = new HashSet<>();
        for (String queueName : allKeys) {
            if (expectedQueue(queueName, recentQueueNames)) {
                continue;
            }
            orphans.add(queueName);
        }
        return valkeyQueue.queuesCounts(orphans);
    }

    private void generateQueueSizeMetrics(Map<String, Long> queuesCounts) {
        Map<String, Long> metrics = new HashMap<>();
        for (Entry<String, Long> entry : queuesCounts.entrySet()) {
            if (entry.getValue() != null) {
                metrics.put("gauge.action_queue.queue_size;queue_name=" + entry.getKey(), entry.getValue());
            }
        }
        metricService.sendGauges(metrics);
    }

    private void addOverThresholdMessage(Map<String, Long> fatQueues, ResultBuilder resultBuilder, int threshold) {
        if (fatQueues.isEmpty()) {
            return;
        }

        resultBuilder.code(CODE_YELLOW);
        resultBuilder.addHeader("Action queues with size over the configured threshold (" + threshold + "):");
        for (Map.Entry<String, Long> fatQueue : fatQueues.entrySet()) {
            resultBuilder.addLine("- " + fatQueue.getKey() + ": __" + fatQueue.getValue() + "__");
        }
        resultBuilder.addLine("\n_Threshold property: checkActionQueueSizeThreshold_");
    }

    private void addOrphanMessage(Map<String, Long> orphans, ResultBuilder resultBuilder) {
        if (orphans.isEmpty()) {
            return;
        }

        resultBuilder.code(CODE_YELLOW);
        resultBuilder.addHeader("Orphan Queues");
        for (Map.Entry<String, Long> orphanQueue : orphans.entrySet()) {
            resultBuilder.addLine("- " + orphanQueue.getKey() + ": __" + orphanQueue.getValue() + "__");
        }
    }

    private boolean expectedQueue(String queueName, Set<String> recentQueueNames) {
        return IGNORED_QUEUE_NAMES.contains(queueName) ||
                queueName.startsWith(ValkeyKeyedBlockingQueue.SSE_VALKEY_CHANNEL_PREFIX) ||
                queueName.startsWith("gauge.node") ||
                recentQueueNames.contains(queueName);
    }
}
