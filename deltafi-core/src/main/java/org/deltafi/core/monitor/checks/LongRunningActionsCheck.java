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
package org.deltafi.core.monitor.checks;

import org.deltafi.common.types.ActionExecution;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.deltafi.core.services.CoreEventQueue;

import java.util.*;

@MonitorProfile
public class LongRunningActionsCheck extends StatusCheck {

    private static final String DESCRIPTION = "Long Running Actions Check";
    private final CoreEventQueue coreEventQueue;

    public LongRunningActionsCheck(CoreEventQueue coreEventQueue) {
        super(DESCRIPTION);
        this.coreEventQueue = coreEventQueue;
    }

    @Override
    public CheckResult check() {
        Collection<LongAction> longRunningActions = longRunningActions();
        ResultBuilder resultBuilder = new ResultBuilder();

        if (!longRunningActions.isEmpty()) {
            this.setDescription( DESCRIPTION + " (" + longRunningActions.size() + ")");
            resultBuilder.addHeader("Actions with long running tasks:");
            for (LongAction action : longRunningActions) {
                resultBuilder.addLine("- " + action.name());
                Collections.sort(action.dids);
                for (UUID did : action.dids()) {
                    String didString = did.toString();
                    String shortDid = didString.substring(0, 7);
                    String didLink = "[" + shortDid + "](/deltafile/viewer/" + didString + ")";
                    resultBuilder.addLine("    - " + didLink + " - Running >" + action.duration() + " seconds");
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
            LongAction task = result.computeIfAbsent(actionExecution.action(), action -> new LongAction(action, seconds));
            task.dids().add(actionExecution.did());
        }

        return result.values();
    }

    private record LongAction(String name, long duration, List<UUID> dids) {
        public LongAction(String name, long duration) {
            this(name, duration, new ArrayList<>());
        }
    }
}
