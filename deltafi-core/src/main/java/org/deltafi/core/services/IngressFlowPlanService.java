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
package org.deltafi.core.services;

import org.deltafi.core.repo.IngressFlowPlanRepo;
import org.deltafi.core.types.FlowPlanInput;
import org.deltafi.core.types.IngressFlow;
import org.deltafi.core.types.IngressFlowPlan;
import org.deltafi.core.validation.IngressFlowPlanValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class IngressFlowPlanService extends FlowPlanService<IngressFlowPlan, IngressFlow> {

    public IngressFlowPlanService(IngressFlowPlanValidator ingressFlowPlanValidator, IngressFlowPlanRepo flowPlanRepo, IngressFlowService flowService) {
        super(ingressFlowPlanValidator, flowPlanRepo, flowService, IngressFlowPlan.class);
    }

    @Override
    IngressFlowPlan mapFromInput(FlowPlanInput flowPlanInput) {
        IngressFlowPlan flowPlan = OBJECT_MAPPER.convertValue(flowPlanInput, IngressFlowPlan.class);

        if (null == flowPlan.getTransformActions()) {
            flowPlan.setTransformActions(new ArrayList<>());
        }

        return flowPlan;
    }
}
