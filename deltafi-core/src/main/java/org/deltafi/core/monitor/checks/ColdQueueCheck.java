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
import org.deltafi.core.generated.types.DeltaFileStats;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.deltafi.core.repo.DeltaFileFlowRepo;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.services.DeltaFiPropertiesService;

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
    // This is the max total in flight for counting total cold queued
    static final int MAX_IN_FLIGHT_COUNT = 3 * MAX_COLD_QUEUE_FOR_GROUPING;
    // last 1 minute when scheduler is every 5 sec
    static final int MAX_SIZE_HISTORY = 12;
    static final DecimalFormat FORMATTER = new DecimalFormat("##,###,###");

    private final DeltaFileRepo deltaFileRepo;
    private final DeltaFileFlowRepo deltaFileFlowRepo;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final ValkeyKeyedBlockingQueue valkeyQueue;

    private final Map<String, ColdQueueHistory> recentlyColdQueued = new HashMap<>();

    public ColdQueueCheck(DeltaFileRepo deltaFileRepo, DeltaFileFlowRepo deltaFileFlowRepo,
                          DeltaFiPropertiesService deltaFiPropertiesService, ValkeyKeyedBlockingQueue valkeyQueue) {
        super("Cold Queued Actions Check");
        this.deltaFileRepo = deltaFileRepo;
        this.deltaFileFlowRepo = deltaFileFlowRepo;
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
        // The more IN_FLIGHT DeltaFiles there are, the slower the queries to
        // count COLD_QUEUED get. Mitigate slow postgres performance by
        // checking other counts first
        Long totalColdQueued = null;
        DeltaFileStats stats = deltaFileRepo.deltaFileStats();
        if (stats.getInFlightCount() < MAX_IN_FLIGHT_COUNT) {
            totalColdQueued = deltaFileFlowRepo.coldQueuedCount(MAX_COLD_QUEUE_FOR_GROUPING);
        }

        // Store the result in Valkey for use by all instances
        if (totalColdQueued != null) {
            try {
                valkeyQueue.set(COLD_QUEUE_COUNT_KEY, String.valueOf(totalColdQueued));
            } catch (Exception e) {
                log.error("Failed to store cold queue count in Valkey", e);
            }
        }

        if (totalColdQueued == null || totalColdQueued >= MAX_COLD_QUEUE_FOR_GROUPING) {
            recentlyColdQueued.clear();
            return checkTotalOnly(totalColdQueued, stats.getInFlightCount());
        } else {
            return checkWithHistory(totalColdQueued);
        }
    }

    CheckResult checkTotalOnly(Long totalColdQueued, long inFlight) {
        ResultBuilder resultBuilder = new ResultBuilder();
        resultBuilder.code(CODE_YELLOW);
        resultBuilder.addHeader("Actions with cold queues:");
        List<String> coldQueuedActions = deltaFileFlowRepo.distinctColdQueuedActions();
        for (String action : coldQueuedActions) {
            resultBuilder.addLine("- " + action);
            resultBuilder.code(CODE_YELLOW);
        }
        resultBuilder.addLine("");
        if (totalColdQueued != null) {
            resultBuilder.addLine("There are at least " + FORMATTER.format(totalColdQueued) + " entries cold queued");
        } else {
            resultBuilder.addLine("Cold queue count not computed due to " + FORMATTER.format(inFlight) + " DeltaFiles In-flight");
        }
        return result(resultBuilder);
    }

    CheckResult checkWithHistory(Long totalColdQueued) {
        ResultBuilder resultBuilder = new ResultBuilder();
        Map<String, Integer> coldQueues = deltaFileFlowRepo.coldQueuedActionsCount();
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
