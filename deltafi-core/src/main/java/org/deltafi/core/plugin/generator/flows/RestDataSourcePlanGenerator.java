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
package org.deltafi.core.plugin.generator.flows;

import org.deltafi.common.types.*;
import org.springframework.stereotype.Service;


@Service
public class RestDataSourcePlanGenerator {

    static final String FLOW_NAME_POSTFIX = "-rest-data-source";

    /**
     * Create a RestDataSourcePlan given the base dataSource name 'baseFlowName'.
     * @param baseFlowName prefix for the dataSource plan name
     * @return a RestDataSourcePlan
     */
    public RestDataSourcePlan generateRestDataSourceFlowPlan(String baseFlowName) {
        String planName = baseFlowName + FLOW_NAME_POSTFIX;
        String topic = baseFlowName + TransformFlowPlanGenerator.FLOW_NAME_POSTFIX;
        return new RestDataSourcePlan(planName, FlowType.REST_DATA_SOURCE, "Sample ReST data source", topic);
    }

}
