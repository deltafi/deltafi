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

import org.deltafi.common.types.ActionExecution;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.deltafi.core.services.CoreEventQueue;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static org.deltafi.core.monitor.checks.CheckResult.CODE_YELLOW;

@MonitorProfile
public class LongRunningActionsCheck extends StatusCheck {

    private static final String DESCRIPTION = "Long Running Actions Check";
    private final CoreEventQueue coreEventQueue;
    private final DeltaFiPropertiesService propertiesService;

    public LongRunningActionsCheck(CoreEventQueue coreEventQueue, DeltaFiPropertiesService propertiesService) {
        super(DESCRIPTION);
        this.coreEventQueue = coreEventQueue;
        this.propertiesService = propertiesService;
    }

    @Override
    public CheckResult check() {
        long warningThreshold = propertiesService.getDeltaFiProperties().getActionExecutionWarning() != null ? propertiesService.getDeltaFiProperties().getActionExecutionWarning().getSeconds() : 0L;
        Collection<LongAction> longRunningActions = longRunningActions();
        ResultBuilder resultBuilder = new ResultBuilder();

        if (!longRunningActions.isEmpty()) {
            this.setDescription(DESCRIPTION + " (" + longRunningActions.size() + ")");
            resultBuilder.addHeader("Actions with long running tasks:");
            for (LongAction action : longRunningActions) {
                resultBuilder.addLine("- " + action.name());
                for (Map.Entry<LongActionDetails, Long> didToTime : action.didTimes().entrySet()) {
                    if (warningThreshold != 0L&& didToTime.getValue() >= warningThreshold) {
                        resultBuilder.code(CODE_YELLOW);
                    }
                    String didString = didToTime.getKey().did.toString();
                    String shortDid = didString.substring(0, 7);
                    String didLink = "[" + shortDid + "](/deltafile/viewer/" + didString + ")";
                    resultBuilder.addLine("    - " + didToTime.getKey().appName + " thread " + didToTime.getKey().threadNum + " " + didLink + " - Running >" + didToTime.getValue() + " seconds");
                }
            }
        } else {
            this.setDescription(DESCRIPTION);
        }

        return result(resultBuilder);
    }

    private Collection<LongAction> longRunningActions() {
        Map<String, LongAction> result = new HashMap<>();
        List<ActionExecution> longRunningActions = coreEventQueue.getLongRunningTasks();

        for (ActionExecution actionExecution : longRunningActions) {
            long seconds = actionExecution.heartbeatTime().toEpochSecond() - actionExecution.startTime().toEpochSecond();
            LongAction task = result.computeIfAbsent(actionExecution.action(), LongAction::new);
            task.didTimes().put(new LongActionDetails(actionExecution.did(), actionExecution.threadNum(), actionExecution.appName()), seconds);
        }

        return result.values();
    }

    private record LongAction(String name, Map<LongActionDetails, Long> didTimes) {
        public LongAction(String name) {
            this(name, new TreeMap<>());
        }
    }

    private record LongActionDetails(UUID did, int threadNum, String appName) implements Comparable<LongActionDetails> {
        @Override
        public int compareTo(@NotNull LongActionDetails o) {
            return did.compareTo(o.did) != 0 ? did.compareTo(o.did)
                    : threadNum != o.threadNum ? Integer.compare(threadNum, o.threadNum)
                    : appName.compareTo(o.appName);
        }
    }
}
