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
package org.deltafi.core.monitor;

import lombok.Builder;
import org.deltafi.core.monitor.checks.CheckResult;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Builder
public record MonitorResult(int code, String color, String state, List<CheckResult> checks, OffsetDateTime timestamp) {
    public static MonitorResult statuses(List<CheckResult> checkResults) {
        OffsetDateTime now = OffsetDateTime.now();
        checkResults.sort(Comparator.comparingInt(CheckResult::code).reversed().thenComparing(CheckResult::description));
        int overallStatus = checkResults.stream().mapToInt(CheckResult::code).max().orElse(-1);
        MonitorResultBuilder builder = MonitorResult.builder()
                .code(overallStatus)
                .checks(checkResults)
                .timestamp(now);

        switch (overallStatus) {
            case 0 -> builder.color("green").state("Healthy");
            case 1 -> builder.color("yellow").state("Degraded");
            case 2 -> builder.color("red").state("Unhealthy");
            default -> builder.color("Unknown").state("Unknown");
        }

        return builder.build();
    }
}
