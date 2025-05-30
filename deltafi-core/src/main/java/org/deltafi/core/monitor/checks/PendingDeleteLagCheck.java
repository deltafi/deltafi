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

import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.deltafi.core.repo.PendingDeleteRepo;

import java.time.Duration;
import java.util.Map;

import static org.deltafi.core.monitor.checks.CheckResult.CODE_RED;

@MonitorProfile
public class PendingDeleteLagCheck extends StatusCheck {

    private final PendingDeleteRepo pendingDeleteRepo;
    private static final Duration AGE_THRESHOLD = Duration.ofSeconds(30);

    public PendingDeleteLagCheck(PendingDeleteRepo pendingDeleteRepo) {
        super("Pending Delete Lag Check");
        this.pendingDeleteRepo = pendingDeleteRepo;
    }

    @Override
    public CheckResult check() {
        ResultBuilder resultBuilder = new ResultBuilder();

        Map<String, Integer> laggingNodes = pendingDeleteRepo.countOldEntriesPerNode(AGE_THRESHOLD);

        if (!laggingNodes.isEmpty()) {
            resultBuilder.code(CODE_RED);
            resultBuilder.addHeader("Nodes with stale pending delete entries (older than 1 minute):");
            laggingNodes.forEach((node, count) -> resultBuilder.addLine("- " + node + ": __" + count + "__ entries"));
            resultBuilder.addLine("");
            resultBuilder.addLine("Ensure deltafi-node-fastdelete is running and check the logs");
        }

        return result(resultBuilder);
    }
}