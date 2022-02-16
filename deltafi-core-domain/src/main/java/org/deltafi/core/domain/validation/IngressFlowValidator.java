package org.deltafi.core.domain.validation;

import org.deltafi.core.domain.configuration.DeltafiRuntimeConfiguration;
import org.deltafi.core.domain.configuration.IngressFlowConfiguration;
import org.deltafi.core.domain.configuration.LoadActionConfiguration;
import org.deltafi.core.domain.configuration.TransformActionConfiguration;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
public class IngressFlowValidator implements RuntimeConfigValidator<IngressFlowConfiguration> {

    @Override
    public Optional<String> validate(DeltafiRuntimeConfiguration configuration, IngressFlowConfiguration ingressFlowConfiguration) {
        List<String> errors = runValidateIngressFlow(configuration, ingressFlowConfiguration);
        if (!errors.isEmpty()) {
            return Optional.of("Ingress Flow Configuration: " + ingressFlowConfiguration + " has the following errors: \n" + String.join("; ", errors));
        }

        return Optional.empty();
    }

    @Override
    public String getName() {
        return "Ingress Flow";
    }

    List<String> runValidateIngressFlow(DeltafiRuntimeConfiguration config, IngressFlowConfiguration ingressFlowConfiguration) {
        List<String> errors = new ArrayList<>();
        String flowType = ingressFlowConfiguration.getType();

        if (isBlank(flowType)) {
            errors.add("Required property type is not set");
        }

        if (isBlank(ingressFlowConfiguration.getName())) {
            errors.add("Required property name is not set");
        }

        List<TransformActionConfiguration> transformActionConfigurations = getTransformConfigs(config, ingressFlowConfiguration, errors);
        LoadActionConfiguration loadActionConfiguration = getLoadConfig(config, ingressFlowConfiguration, errors);

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

    List<TransformActionConfiguration> getTransformConfigs(DeltafiRuntimeConfiguration deltafiRuntimeConfiguration, IngressFlowConfiguration ingressFlowConfiguration, List<String> errors) {
        List<TransformActionConfiguration> transformActionConfigurations = new ArrayList<>();
        for (String name : ingressFlowConfiguration.getTransformActions()) {
            TransformActionConfiguration transformActionConfiguration = deltafiRuntimeConfiguration.getTransformActions().get(name);
            if (isNull(transformActionConfiguration)) {
                errors.add(RuntimeConfigValidator.referenceError("Transform Action", name));
            } else {
                transformActionConfigurations.add(transformActionConfiguration);
            }
        }
        return transformActionConfigurations;
    }

    LoadActionConfiguration getLoadConfig(DeltafiRuntimeConfiguration deltafiRuntimeConfiguration, IngressFlowConfiguration ingressFlowConfiguration, List<String> errors) {
        String loadAction = ingressFlowConfiguration.getLoadAction();
        if (isBlank(loadAction)) {
            errors.add("Required property loadAction must be set");
            return null;
        }

        LoadActionConfiguration loadActionConfiguration = deltafiRuntimeConfiguration.getLoadActions().get(loadAction);
        if (isNull(loadActionConfiguration)) {
            errors.add(RuntimeConfigValidator.referenceError("Load Action", loadAction));
        }
        return loadActionConfiguration;
    }
}
