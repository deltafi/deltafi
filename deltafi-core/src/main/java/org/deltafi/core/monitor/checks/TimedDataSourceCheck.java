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

import org.deltafi.common.types.IngressStatus;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.deltafi.core.services.TimedDataSourceService;
import org.deltafi.core.types.TimedDataSource;

import java.util.List;

import static org.deltafi.core.monitor.checks.CheckResult.*;

@MonitorProfile
public class TimedDataSourceCheck extends StatusCheck {

    private final TimedDataSourceService timedDataSourceService;

    public TimedDataSourceCheck(TimedDataSourceService timedDataSourceService) {
        super("Timed Data Source Check");
        this.timedDataSourceService = timedDataSourceService;
    }

    public CheckResult check() {
        ResultBuilder resultBuilder = new ResultBuilder();
        checkFlows(resultBuilder, timedDataSourceService.getRunningFlows());

        if (resultBuilder.getCode() != 0) {
            resultBuilder.addLine("\nVisit the [Data Sources](/config/data-sources)" +
                    " page for more info.");
        }

        return result(resultBuilder);
    }

    private void checkFlows(ResultBuilder resultBuilder, List<TimedDataSource> flows) {
        if (flows.isEmpty()) {
            return;
        }

        boolean first = true;
        for (TimedDataSource flow : flows) {
            if (flow.getIngressStatus() == IngressStatus.UNHEALTHY) {
                if (first) {
                    resultBuilder.addHeader("Ingress Status is Unhealthy");
                    resultBuilder.code(CODE_YELLOW);
                    first = false;
                }
                resultBuilder.addLine("\n - __" + flow.getName() + "__ (" + flow.getSourcePlugin() + ")");
                if (flow.getIngressStatusMessage() != null) {
                    resultBuilder.addLine("\n   - " + flow.getIngressStatusMessage());
                }
            }
        }
    }
}
