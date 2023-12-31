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
package org.deltafi.core.validation;

import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.types.EgressFlow;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class EgressFlowValidator extends FlowValidator<EgressFlow> {

    public EgressFlowValidator(SchemaComplianceValidator schemaComplianceValidator) {
        super(schemaComplianceValidator);
    }

    @Override
    public List<FlowConfigError> flowSpecificValidation(EgressFlow egressFlow) {
        return excludedAndIncluded(egressFlow);
    }

    List<FlowConfigError> excludedAndIncluded(EgressFlow egressFlow) {
        if (Objects.nonNull(egressFlow.getExcludeNormalizeFlows()) && Objects.nonNull(egressFlow.getIncludeNormalizeFlows())) {
            return egressFlow.getExcludeNormalizeFlows().stream()
                    .filter(flowName -> egressFlow.getIncludeNormalizeFlows().contains(flowName))
                    .map(flowName -> excludedAndIncludedError(egressFlow.getName(), flowName))
                    .toList();
        }

        return Collections.emptyList();
    }

    FlowConfigError excludedAndIncludedError(String egressFlow, String normalizeFlow) {
        FlowConfigError configError = new FlowConfigError();
        configError.setConfigName(egressFlow);
        configError.setErrorType(FlowErrorType.INVALID_CONFIG);
        configError.setMessage("Flow: " + normalizeFlow + " is both included and excluded");
        return configError;
    }
}
