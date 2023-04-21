/**
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
package org.deltafi.core.validation;

import lombok.AllArgsConstructor;
import org.deltafi.common.types.IngressFlowPlan;
import org.deltafi.common.types.TransformFlowPlan;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.repo.TransformFlowPlanRepo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Service
public class IngressFlowPlanValidator extends FlowPlanValidator<IngressFlowPlan> {
    private final TransformFlowPlanRepo transformFlowPlanRepo;

    /**
     * Flow plan type specific validation checks
     * Cross-check the ingress flow plan names against the transform flow plan names
     * @return list of errors
     */
    @Override
    public List<FlowConfigError> flowPlanSpecificValidation(IngressFlowPlan flowPlan) {
        List<FlowConfigError> errors = new ArrayList<>();

        TransformFlowPlan existingTransformFlow = transformFlowPlanRepo.findById(flowPlan.getName()).orElse(null);
        if (existingTransformFlow != null) {
            errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                    .configName(flowPlan.getName())
                    .message("Cannot add ingress flow plan, a transform flow plan with the name: " + flowPlan.getName() + " already exists in plugin: " + existingTransformFlow.getSourcePlugin()).build());
        }

        return errors;
    }
}
