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

import org.deltafi.core.exceptions.StorageCheckException;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.SystemService;
import org.deltafi.core.types.DiskMetrics;

import static org.deltafi.core.monitor.checks.CheckResult.CODE_RED;
import static org.deltafi.core.monitor.checks.CheckResult.CODE_YELLOW;

@MonitorProfile
public class ContentStorageCheck extends StatusCheck {

    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final SystemService systemService;

    public ContentStorageCheck(DeltaFiPropertiesService deltaFiPropertiesService, SystemService systemService) {
        super("Content Storage Check");
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.systemService = systemService;
    }

    public CheckResult check() {
        ResultBuilder resultBuilder = new ResultBuilder();
        try {
            int threshold = deltaFiPropertiesService.getDeltaFiProperties().getCheckContentStoragePercentThreshold();
            int usage = systemService.contentNodesDiskMetrics().stream().map(DiskMetrics::percentUsedFloor).reduce(0, Integer::max);

            if (usage > threshold) {
                resultBuilder.code(CODE_YELLOW);
                resultBuilder.addHeader("Content storage usage (" + usage + "%)" +
                        " is over the configured threshold (" + threshold + "%):");
                resultBuilder.addLine("_Threshold property: checkContentStoragePercentThreshold_");
            }
        } catch (StorageCheckException e) {
            resultBuilder.code(CODE_RED);
            resultBuilder.addLine(e.getMessage());
        }

        return result(resultBuilder);
    }
}
