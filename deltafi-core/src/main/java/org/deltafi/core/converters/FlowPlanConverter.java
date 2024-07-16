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
package org.deltafi.core.converters;

import org.deltafi.common.types.Variable;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.Flow;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.core.types.FlowPlanEntity;

import java.util.ArrayList;
import java.util.List;

public abstract class FlowPlanConverter<FlowPlanT extends FlowPlanEntity, FlowT extends Flow> {

    public FlowT convert(FlowPlanT flowPlan, List<Variable> variables) {
        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables);

        FlowT flow = createFlow(flowPlan, flowPlanPropertyHelper);

        flow.setDescription(flowPlan.getDescription());
        flow.setName(flowPlan.getName());
        flow.setSourcePlugin(flowPlan.getSourcePlugin());

        List<FlowConfigError> configErrors = new ArrayList<>(flowPlanPropertyHelper.getErrors());

        FlowState state = configErrors.isEmpty() ? FlowState.STOPPED : FlowState.INVALID;
        flow.getFlowStatus().setState(state);
        flow.getFlowStatus().getErrors().addAll(configErrors);

        flow.setVariables(flowPlanPropertyHelper.getAppliedVariables());
        return flow;
    }

    /**
     * Convert the given FlowPlan to a Flow using the given variables
     * to resolve any placeholders in the plan.
     * @param flowPlanT template that will be used to create the flow
     * @param flowPlanPropertyHelper holds the variables used to resolve templated fields
     */
    public abstract FlowT createFlow(FlowPlanT flowPlanT, FlowPlanPropertyHelper flowPlanPropertyHelper);

}
