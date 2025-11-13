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

import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.deltafi.core.repo.PendingDeleteRepo;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Duration;
import java.util.Map;

import static org.deltafi.core.monitor.checks.CheckResult.*;

@MonitorProfile
@ConditionalOnProperty(name = "local.storage.content", havingValue = "true", matchIfMissing = true)
public class PendingDeleteLagCheck extends StatusCheck {

    private final PendingDeleteRepo pendingDeleteRepo;
    private final DeltaFiPropertiesService propertiesService;
    private static final Duration AGE_THRESHOLD = Duration.ofSeconds(30);

    public PendingDeleteLagCheck(DeltaFiPropertiesService propertiesService, PendingDeleteRepo pendingDeleteRepo) {
        super("Pending Delete Lag Check");
        this.pendingDeleteRepo = pendingDeleteRepo;
        this.propertiesService = propertiesService;
    }

    @Override
    public CheckResult check() {
        ResultBuilder resultBuilder = new ResultBuilder();

        Map<String, Integer> laggingNodes = pendingDeleteRepo.countOldEntriesPerNode(AGE_THRESHOLD);

        DeltaFiProperties properties = propertiesService.getDeltaFiProperties();
        int warningThreshold = properties.getCheckDeleteLagWarningThreshold();
        int errorThreshold = properties.getCheckDeleteLagErrorThreshold();
        int code = CODE_GREEN;
        if (!laggingNodes.isEmpty()) {
            resultBuilder.addHeader("Nodes with stale pending delete entries (older than 1 minute):");
            for (Map.Entry<String, Integer> entry : laggingNodes.entrySet()) {
                int count = entry.getValue() != null ? entry.getValue() : 0;
                if (count >= errorThreshold) {
                    code = CODE_RED;
                } else if (count >= warningThreshold && code != CODE_RED) {
                    code = CODE_YELLOW;
                }
                resultBuilder.addLine("- " + entry.getKey() + ": __" + count + "__ entries");
            }
            resultBuilder.addLine("");
            resultBuilder.addLine("Ensure deltafi-node-fastdelete is running and check the logs");
        }
        resultBuilder.code(code);

        return result(resultBuilder);
    }
}