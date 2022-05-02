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
package org.deltafi.core.domain.validation;

import lombok.AllArgsConstructor;
import org.deltafi.core.domain.configuration.*;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowErrorType;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.types.EgressFlow;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class EgressFlowValidator {

    private final SchemaCompliancyValidator schemaCompliancyValidator;

    public void validate(EgressFlow egressFlow) {
        // preserve any variable errors
        List<FlowConfigError> errors = egressFlow.getFlowStatus()
                .getErrors().stream().filter(error -> FlowErrorType.UNRESOLVED_VARIABLE.equals(error.getErrorType()))
                .collect(Collectors.toList());

        errors.addAll(CommonFlowValidator.validateConfigurationNames(egressFlow));

        errors.addAll(validateActions(egressFlow.getEnrichActions()));
        errors.addAll(validateAction(egressFlow.getFormatAction()));
        errors.addAll(validateAction(egressFlow.getEgressAction()));
        errors.addAll(validateActions(egressFlow.getValidateActions()));
        errors.addAll(excludedAndIncluded(egressFlow));

        if (!errors.isEmpty()) {
            egressFlow.getFlowStatus().setState(FlowState.INVALID);
        } else if(FlowState.INVALID.equals(egressFlow.getFlowStatus().getState())) {
            egressFlow.getFlowStatus().setState(FlowState.STOPPED);
        }

        egressFlow.getFlowStatus().setErrors(errors);
    }

    List<FlowConfigError> validateActions(List<? extends ActionConfiguration> actionConfigurations) {
        if (Objects.isNull(actionConfigurations)) {
            return Collections.emptyList();
        }

        return actionConfigurations.stream()
                .map(this::validateAction)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    List<FlowConfigError> validateAction(ActionConfiguration actionConfiguration) {
        return schemaCompliancyValidator.validate(actionConfiguration);
    }

    List<FlowConfigError> excludedAndIncluded(EgressFlow egressFlow) {
        if (Objects.nonNull(egressFlow.getExcludeIngressFlows()) && Objects.nonNull(egressFlow.getIncludeIngressFlows())) {
            return egressFlow.getExcludeIngressFlows().stream()
                    .filter(flowName -> egressFlow.getIncludeIngressFlows().contains(flowName))
                    .map(flowName -> excludedAndIncludedError(egressFlow.getName(), flowName))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    FlowConfigError excludedAndIncludedError(String egressFlow, String ingressFlow) {
        FlowConfigError configError = new FlowConfigError();
        configError.setConfigName(egressFlow);
        configError.setErrorType(FlowErrorType.INVALID_CONFIG);
        configError.setMessage("Flow: " + ingressFlow + " is both included and excluded");
        return configError;
    }
}
