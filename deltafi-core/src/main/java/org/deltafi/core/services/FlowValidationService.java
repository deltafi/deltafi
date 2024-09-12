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

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlowValidationService {
    private final List<FlowPlanService<?, ?, ?, ?>> flowPlanServices;
    private final List<FlowService<?, ?, ?, ?>> flowServices;

    public FlowValidationService(List<FlowPlanService<?, ?, ?, ?>> flowPlanServices, List<FlowService<?, ?, ?, ?>> flowServices) {
        this.flowPlanServices = flowPlanServices;
        this.flowServices = flowServices;
    }

    /**
     * Rebuild all invalid flows and then run validate
     * against all flows.
     */
    public void revalidateFlows() {
        rebuildInvalidFlows();
        validateAllFlows();
    }

    private void rebuildInvalidFlows() {
        flowPlanServices.forEach(FlowPlanService::rebuildInvalidFlows);
    }

    private void validateAllFlows() {
        flowServices.forEach(FlowService::validateAllFlows);
    }
}
