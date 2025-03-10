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
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.SystemService;
import org.deltafi.core.types.DiskMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.deltafi.core.monitor.checks.CheckResult.CODE_YELLOW;

@MonitorProfile
public class StorageCheck extends StatusCheck {

    private final SystemService systemService;
    private final DeltaFiPropertiesService propertiesService;

    public StorageCheck(SystemService systemService, DeltaFiPropertiesService propertiesService) {
        super("Storage Check");
        this.systemService = systemService;
        this.propertiesService = propertiesService;
    }

    @Override
    public CheckResult check() {
        ResultBuilder resultBuilder = new ResultBuilder();
        Map<String, DiskMetrics> allDiskMetrics = systemService.allDiskMetrics();
        int threshold = propertiesService.getDeltaFiProperties().getCheckContentStoragePercentThreshold();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, DiskMetrics> entry : allDiskMetrics.entrySet()) {
            int used = entry.getValue().percentUsedFloor();
            if (used > threshold) {
                errors.add("__" + entry.getKey() + ":/data/deltafi__ is at __" + used + "%__");
            }
        }

        if (!errors.isEmpty()) {
            resultBuilder.code(CODE_YELLOW);
            resultBuilder.addHeader("Nodes with disk usage over threshold (" + threshold + "%)");
            errors.forEach(e -> resultBuilder.addLine("- " + e));
        }


        return result(resultBuilder);
    }
}
