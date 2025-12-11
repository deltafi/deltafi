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
// ABOUTME: Health check that monitors plugin installation status.
// ABOUTME: Reports failed or in-progress installations to the system status.
package org.deltafi.core.monitor.checks;

import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.services.PluginService.PluginStateSummary;
import org.deltafi.core.types.PluginEntity;

import static org.deltafi.core.monitor.checks.CheckResult.CODE_GREEN;
import static org.deltafi.core.monitor.checks.CheckResult.CODE_RED;
import static org.deltafi.core.monitor.checks.CheckResult.CODE_YELLOW;

@MonitorProfile
public class PluginInstallCheck extends StatusCheck {

    private final PluginService pluginService;

    public PluginInstallCheck(PluginService pluginService) {
        super("Plugin Installation Check");
        this.pluginService = pluginService;
    }

    @Override
    public CheckResult check() {
        PluginStateSummary summary = pluginService.getInstallSummary();

        if (summary.allHealthy()) {
            return result(CODE_GREEN, "All " + summary.installed() + " plugin(s) are installed and healthy.");
        }

        CheckResult.ResultBuilder resultBuilder = new CheckResult.ResultBuilder();

        if (summary.anyFailed()) {
            resultBuilder.code(CODE_RED);
            resultBuilder.addHeader("Failed Plugin Installations");
            for (PluginEntity plugin : summary.failedPlugins()) {
                resultBuilder.addLine("\n - __" + plugin.getPluginCoordinates().groupAndArtifact() + "__");
                if (plugin.getInstallError() != null) {
                    resultBuilder.addLine("\n   - Error: " + plugin.getInstallError());
                }
                resultBuilder.addLine("\n   - Attempts: " + plugin.getInstallAttempts());
            }
            resultBuilder.addLine("\n\nVisit the [Plugins](/config/plugins) page to retry failed installations.");
        }

        if (summary.anyInProgress()) {
            resultBuilder.code(CODE_YELLOW);
            StringBuilder inProgress = new StringBuilder();
            if (summary.pending() > 0) {
                inProgress.append(summary.pending()).append(" pending");
            }
            if (summary.installing() > 0) {
                if (!inProgress.isEmpty()) inProgress.append(", ");
                inProgress.append(summary.installing()).append(" installing");
            }
            if (summary.removing() > 0) {
                if (!inProgress.isEmpty()) inProgress.append(", ");
                inProgress.append(summary.removing()).append(" removing");
            }
            resultBuilder.addHeader("Plugins In Progress");
            resultBuilder.addLine(inProgress.toString());
        }

        return result(resultBuilder);
    }
}
