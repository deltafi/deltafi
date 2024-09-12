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
package org.deltafi.core.services;

import org.deltafi.common.types.FlowType;
import org.deltafi.core.repo.EgressFlowRepo;
import org.deltafi.core.repo.FlowPlanRepo;
import org.deltafi.core.types.snapshot.EgressFlowSnapshot;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.EgressFlowPlanEntity;
import org.deltafi.core.validation.EgressFlowPlanValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
public class EgressFlowPlanService extends FlowPlanService<EgressFlowPlanEntity, EgressFlow, EgressFlowSnapshot, EgressFlowRepo> {
    public EgressFlowPlanService(EgressFlowPlanValidator egressFlowPlanValidator, FlowPlanRepo flowPlanRepo, EgressFlowService flowService, BuildProperties buildProperties) {
        super(egressFlowPlanValidator, flowPlanRepo, flowService, buildProperties);
    }

    @Override
    protected FlowType getFlowType() {
        return FlowType.EGRESS;
    }

    @Override
    protected Class<EgressFlowPlanEntity> getFlowPlanClass() {
        return EgressFlowPlanEntity.class;
    }
}
