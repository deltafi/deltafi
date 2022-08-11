/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.converters;

import org.deltafi.common.types.Variable;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.types.Flow;
import org.deltafi.core.domain.types.FlowPlan;

import java.util.ArrayList;
import java.util.List;

public abstract class FlowPlanConverter<FlowPlanT extends FlowPlan, FlowT extends Flow> {

    public FlowT convert(FlowPlanT flowPlan, List<Variable> variables) {
        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables, flowPlan.getName());

        FlowT flow = getFlowInstance();

        flow.setDescription(flowPlan.getDescription());
        flow.setName(flowPlan.getName());
        flow.setSourcePlugin(flowPlan.getSourcePlugin());

        populateFlowSpecificFields(flowPlan, flow, flowPlanPropertyHelper);

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
     * @param flow that will have the resolved fields populated
     * @param flowPlanPropertyHelper holds the variables used to resolve templated fields
     */
    public abstract void populateFlowSpecificFields(FlowPlanT flowPlanT, FlowT flow, FlowPlanPropertyHelper flowPlanPropertyHelper);

    /**
     * Get a new instance of a flow
     * @return a new instance of a flow
     */
    abstract FlowT getFlowInstance();

}
