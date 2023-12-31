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
package org.deltafi.core.services;

import org.deltafi.common.types.NormalizeFlowPlan;
import org.deltafi.core.repo.NormalizeFlowPlanRepo;
import org.deltafi.core.snapshot.types.NormalizeFlowSnapshot;
import org.deltafi.core.types.NormalizeFlow;
import org.deltafi.core.validation.NormalizeFlowPlanValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
public class NormalizeFlowPlanService extends FlowPlanService<NormalizeFlowPlan, NormalizeFlow, NormalizeFlowSnapshot> {
    public NormalizeFlowPlanService(NormalizeFlowPlanValidator normalizeFlowPlanValidator, NormalizeFlowPlanRepo flowPlanRepo, NormalizeFlowService flowService, BuildProperties buildProperties) {
        super(normalizeFlowPlanValidator, flowPlanRepo, flowService, buildProperties);
    }
}
