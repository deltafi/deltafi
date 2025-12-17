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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.QueueManagementService;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.deltafi.core.monitor.checks.CheckResult.CODE_YELLOW;

@MonitorProfile
@Slf4j
public class ColdQueueCheck extends StatusCheck {
    public static final String COLD_QUEUE_COUNT_KEY = "org.deltafi.cold.queue.count";
    // This is the max total cold queued items for counting per action
    static final int MAX_COLD_QUEUE_FOR_GROUPING = 250_000;
    // last 1 minute when scheduler is every 5 sec
    static final int MAX_SIZE_HISTORY = 12;
    static final DecimalFormat FORMATTER = new DecimalFormat("##,###,###");

    private final QueueManagementService queueManagementService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final ValkeyKeyedBlockingQueue valkeyQueue;

    private final Map<String, ColdQueueHistory> recentlyColdQueued = new HashMap<>();

    public ColdQueueCheck(QueueManagementService queueManagementService,
                          DeltaFiPropertiesService deltaFiPropertiesService,
                          ValkeyKeyedBlockingQueue valkeyQueue) {
        super("Cold Queued Actions Check");
        this.queueManagementService = queueManagementService;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.valkeyQueue = valkeyQueue;
    }

    static void updateHistory(Map<String, ColdQueueHistory> previous, Map<String, Integer> current) {
        Map<String, ColdQueueHistory> newMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : current.entrySet()) {
            ColdQueueHistory history = previous.getOrDefault(entry.getKey(), new ColdQueueHistory());
            history.add(entry.getValue());
            newMap.put(entry.getKey(), history);
        }
        previous.clear();
        previous.putAll(newMap);
    }

    @Override
    public CheckResult check() {
        long totalColdQueued = queueManagementService.coldQueuedCount();

        // Publish count to Valkey for worker instances
        try {
            valkeyQueue.set(COLD_QUEUE_COUNT_KEY, String.valueOf(totalColdQueued));
        } catch (Exception e) {
            log.error("Failed to publish cold queue count to Valkey", e);
        }

        if (totalColdQueued >= MAX_COLD_QUEUE_FOR_GROUPING) {
            recentlyColdQueued.clear();
            return checkTotalOnly(totalColdQueued);
        } else {
            return checkWithHistory(totalColdQueued);
        }
    }

    CheckResult checkTotalOnly(long totalColdQueued) {
        ResultBuilder resultBuilder = new ResultBuilder();
        resultBuilder.code(CODE_YELLOW);
        resultBuilder.addHeader("Actions with cold queues:");
        for (String action : queueManagementService.distinctColdQueuedActions()) {
            resultBuilder.addLine("- " + action);
            resultBuilder.code(CODE_YELLOW);
        }
        resultBuilder.addLine("");
        resultBuilder.addLine("There are at least " + FORMATTER.format(totalColdQueued) + " entries cold queued");
        return result(resultBuilder);
    }

    CheckResult checkWithHistory(long totalColdQueued) {
        ResultBuilder resultBuilder = new ResultBuilder();
        Map<String, Integer> coldQueues = queueManagementService.coldQueuedActionsCount();
        if (coldQueues.isEmpty()) {
            recentlyColdQueued.clear();
            return result(resultBuilder);
        }

        int minimumGrowingCheck = deltaFiPropertiesService.getDeltaFiProperties().getCheckColdQueueMinimumGrowing();
        int warningThreshold = deltaFiPropertiesService.getDeltaFiProperties().getCheckColdQueueWarningThreshold();

        updateHistory(recentlyColdQueued, coldQueues);

        resultBuilder.addHeader("Actions with cold queues:");
        for (Map.Entry<String, ColdQueueHistory> entry : recentlyColdQueued.entrySet()) {
            resultBuilder.addLine(entry.getValue().getMessage(entry.getKey()));
            if (entry.getValue().computeIsWarning(minimumGrowingCheck, warningThreshold)) {
                resultBuilder.code(CODE_YELLOW);
            }
            resultBuilder.addLine("");
        }
        resultBuilder.addLine("Total cold queued: " + FORMATTER.format(totalColdQueued));
        return result(resultBuilder);
    }

    static class ColdQueueHistory {
        List<Integer> counts = new ArrayList<>();

        ColdQueueHistory() {
        }

        int historySize() {
            return counts.size();
        }

        void add(Integer currentCount) {
            counts.add(currentCount);
            while (counts.size() > MAX_SIZE_HISTORY) {
                // age-off oldest
                counts.removeFirst();
            }
        }

        public boolean computeIsWarning(int minimumGrowingCheck, int warningThreshold) {
            if (counts.getLast() > warningThreshold) {
                return true;
            }
            int avg = average();
            return avg > 0 && counts.getLast() >= minimumGrowingCheck
                    && counts.getLast() >= avg;
        }

        int average() {
            if (counts.size() < 2) {
                return 0;
            }
            int sum = 0;
            // ignore the last (most recent) entry
            for (Integer i : counts.subList(0, counts.size() - 1)) {
                sum += i;
            }
            return sum / (counts.size() - 1);
        }

        String previous() {
            if (counts.size() > 1) {
                return "(was " + FORMATTER.format(counts.get(counts.size() - 2)) + ")";
            }
            return " (was 0)";
        }

        public String getMessage(String name) {
            return "- " + name + ": __" + FORMATTER.format(counts.getLast()) + "__ " + previous();
        }
    }
}
