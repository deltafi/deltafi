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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.configuration.LoadActionConfiguration;
import org.deltafi.core.domain.configuration.TransformActionConfiguration;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowErrorType;
import org.deltafi.core.domain.types.IngressFlow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
@Service
public class IngressFlowValidator extends FlowValidator<IngressFlow> {

    public IngressFlowValidator(SchemaCompliancyValidator schemaCompliancyValidator) {
        super(schemaCompliancyValidator);
    }

    @Override
    public List<FlowConfigError> flowSpecificValidation(IngressFlow ingressFlow) {
        // TODO - refactor the consumes/produces checks once it's determined what checks are necessary
        //runValidateIngressFlow(ingressFlow);

        return isBlank(ingressFlow.getType()) ? List.of(blankTypeError(ingressFlow.getType())) : List.of();
    }

    FlowConfigError blankTypeError(String name) {
        return FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                .configName(name)
                .message("The ingress flow type cannot be blank").build();
    }


    List<String> runValidateIngressFlow(IngressFlow ingressFlow) {
        List<String> errors = new ArrayList<>();
        String flowType = ingressFlow.getType();

        if (isBlank(flowType)) {
            errors.add("Required property type is not set");
        }

        if (isBlank(ingressFlow.getName())) {
            errors.add("Required property name is not set");
        }

        List<TransformActionConfiguration> transformActionConfigurations = ingressFlow.getTransformActions();
        LoadActionConfiguration loadActionConfiguration = ingressFlow.getLoadAction();

        // if a referenced action could not be found we cannot do further validation of the flow
        if (!errors.isEmpty()) {
            return errors;
        }

        Set<String> producedTypes = transformActionConfigurations.stream().map(org.deltafi.core.domain.generated.types.TransformActionConfiguration::getProduces).collect(Collectors.toSet());

        errors.addAll(validateTransformsAreReachable(transformActionConfigurations, producedTypes, flowType));
        errors.addAll(validateLoadActionIsReachable(loadActionConfiguration, producedTypes, flowType));

        return errors;
    }

    List<String> validateTransformsAreReachable(List<TransformActionConfiguration> transformActionConfigurations, Set<String> producedTypes, String flowType) {
        if (transformActionConfigurations.isEmpty()) {
            return emptyList();
        }

        List<String> errors = new ArrayList<>();

        boolean handlesIngressType = false;
        for (TransformActionConfiguration transformActionConfiguration : transformActionConfigurations) {
            Set<String> othersProduce = new HashSet<>(producedTypes);
            othersProduce.remove(transformActionConfiguration.getProduces());
            String consumes = transformActionConfiguration.getConsumes();
            if (flowType.equals(consumes)) {
                handlesIngressType = true;
            } else if (!othersProduce.contains(transformActionConfiguration.getConsumes())) {
                errors.add("Transform Action named: " + transformActionConfiguration.getName() + " consumes: " + transformActionConfiguration.getConsumes() + " which is not produced in this flow");
            }
        }

        if (!handlesIngressType) {
            errors.add("None of the configured TransformActions in this flow consume the ingress flow type: " + flowType);
        }

        return errors;
    }

    List<String> validateLoadActionIsReachable(LoadActionConfiguration loadActionConfiguration, Set<String> producedTypes, String flowType) {
        List<String> errors = new ArrayList<>();

        // If produceTypes is populated then assume a TransformAction handles the ingress flow type. Otherwise, a LoadAction needs to consume the flowType
        boolean isIngressTypeHandled = !producedTypes.isEmpty();
        String consumes = loadActionConfiguration.getConsumes();
        if (flowType.equals(consumes)) {
            isIngressTypeHandled = true;
        } else if (!producedTypes.contains(loadActionConfiguration.getConsumes())) {
            errors.add("Load Action named: " + loadActionConfiguration.getName() + " consumes: " + loadActionConfiguration.getConsumes() + " which isn't produced in this flow");
        }

        if (!isIngressTypeHandled) {
            errors.add("None of the configured Load Actions in this flow consume the ingress flow type: " + flowType);
        }

        return errors;
    }

}
