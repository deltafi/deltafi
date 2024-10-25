/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.services.DataSinkService;
import org.deltafi.core.services.RestDataSourceService;
import org.deltafi.core.services.TimedDataSourceService;
import org.deltafi.core.services.TransformFlowService;
import org.deltafi.core.types.Flow;

import java.util.List;

@MonitorProfile
public class FlowCheck extends StatusCheck {

    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;
    private final TransformFlowService transformFlowService;
    private final DataSinkService dataSinkService;

    public FlowCheck(RestDataSourceService restDataSourceService, TimedDataSourceService timedDataSourceService,
                     TransformFlowService transformFlowService, DataSinkService dataSinkService) {
        super("Flow Check");
        this.restDataSourceService = restDataSourceService;
        this.timedDataSourceService = timedDataSourceService;
        this.transformFlowService = transformFlowService;
        this.dataSinkService = dataSinkService;
    }

    public CheckResult check() {
        ResultBuilder resultBuilder = new ResultBuilder();
        checkFlows(resultBuilder, "Rest Data Sources", restDataSourceService.getAllInvalidFlows());
        checkFlows(resultBuilder, "Timed Data Sources", timedDataSourceService.getAllInvalidFlows());
        checkFlows(resultBuilder, "Flows", transformFlowService.getAllInvalidFlows());
        checkFlows(resultBuilder, "Egress", dataSinkService.getAllInvalidFlows());

        if (resultBuilder.getCode() != 0) {
             resultBuilder.addLine("\nVisit the [Data Sources](/config/data-sources), [Flows](/config/flows)," +
                     " or [Egress](/config/egress) page for more info.");
        }

        return result(resultBuilder);
    }

    private void checkFlows(ResultBuilder resultBuilder, String flowType, List<? extends Flow> flows) {
        if (flows.isEmpty()) {
            return;
        }

        resultBuilder.code(1);
        resultBuilder.addHeader("Invalid " + flowType);
        for (Flow flow : flows) {
            resultBuilder.addLine("\n - __" + flow.getName() + "__ (" + flow.getSourcePlugin() + ")");
            flow.getFlowStatus().getErrors()
                    .forEach(flowConfigError -> resultBuilder.addLine("\n   - " + flowConfigError.getMessage()));
        }
    }
}
