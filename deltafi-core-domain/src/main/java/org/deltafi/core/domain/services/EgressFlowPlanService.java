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
package org.deltafi.core.domain.services;

import org.deltafi.core.domain.repo.EgressFlowPlanRepo;
import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.EgressFlowPlan;
import org.deltafi.core.domain.types.FlowPlanInput;
import org.deltafi.core.domain.validation.EgressFlowPlanValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class EgressFlowPlanService extends FlowPlanService<EgressFlowPlan, EgressFlow> {

    public EgressFlowPlanService(EgressFlowPlanValidator egressFlowPlanValidator, EgressFlowPlanRepo flowPlanRepo, EgressFlowService flowService) {
        super(egressFlowPlanValidator, flowPlanRepo, flowService, EgressFlowPlan.class);
    }

    @Override
    EgressFlowPlan mapFromInput(FlowPlanInput flowPlanInput) {
        EgressFlowPlan flowPlan = OBJECT_MAPPER.convertValue(flowPlanInput, EgressFlowPlan.class);

        if (null == flowPlan.getExcludeIngressFlows()) {
            flowPlan.setExcludeIngressFlows(new ArrayList<>());
        }

        if (null == flowPlan.getValidateActions()) {
            flowPlan.setValidateActions(new ArrayList<>());
        }

        return flowPlan;
    }
}
