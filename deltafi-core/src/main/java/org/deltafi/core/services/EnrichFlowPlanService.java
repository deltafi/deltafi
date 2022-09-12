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

import org.deltafi.core.repo.EnrichFlowPlanRepo;
import org.deltafi.core.types.EnrichFlow;
import org.deltafi.core.types.EnrichFlowPlan;
import org.deltafi.core.types.FlowPlanInput;
import org.deltafi.core.validation.EnrichFlowPlanValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class EnrichFlowPlanService extends FlowPlanService<EnrichFlowPlan, EnrichFlow> {

    public EnrichFlowPlanService(EnrichFlowPlanValidator enrichFlowPlanValidator, EnrichFlowPlanRepo flowPlanRepo, EnrichFlowService flowService) {
        super(enrichFlowPlanValidator, flowPlanRepo, flowService, EnrichFlowPlan.class);
    }

    @Override
    EnrichFlowPlan mapFromInput(FlowPlanInput flowPlanInput) {
        EnrichFlowPlan flowPlan = OBJECT_MAPPER.convertValue(flowPlanInput, EnrichFlowPlan.class);

        if (null == flowPlan.getEnrichActions()) {
            flowPlan.setEnrichActions(new ArrayList<>());
        }

        return flowPlan;
    }
}
