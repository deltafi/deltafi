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
package org.deltafi.core.validation;

import lombok.AllArgsConstructor;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.types.Flow;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
public abstract class FlowValidator<T extends Flow> {

    private final SchemaComplianceValidator schemaComplianceValidator;

    public List<FlowConfigError> validate(T flow) {
        List<FlowConfigError> errors = new ArrayList<>();

        errors.addAll(validateActions(flow.allActionConfigurations()));
        errors.addAll(flowSpecificValidation(flow));

        return errors;
    }

    /**
     * Run any extra validation specific to the flow type
     * @param flow to validate
     * @return list of configuration errors
     */
    public abstract List<FlowConfigError> flowSpecificValidation(T flow);

    List<FlowConfigError> validateActions(List<? extends ActionConfiguration> actionConfigurations) {
        if (Objects.isNull(actionConfigurations)) {
            return Collections.emptyList();
        }

        return actionConfigurations.stream()
                .map(this::validateAction)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<FlowConfigError> validateAction(ActionConfiguration actionConfiguration) {
        return schemaComplianceValidator.validate(actionConfiguration);
    }
}
