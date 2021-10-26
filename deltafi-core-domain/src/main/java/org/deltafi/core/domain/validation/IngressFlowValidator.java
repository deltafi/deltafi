package org.deltafi.core.domain.validation;

import org.deltafi.core.domain.configuration.*;
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
        List<LoadActionConfiguration> loadActionConfigurations = getLoadConfigs(config, ingressFlowConfiguration, errors);

        // if a referenced action could not be found we cannot do further validation of the flow
        if (!errors.isEmpty()) {
            return errors;
        }

        Set<String> producedTypes = transformActionConfigurations.stream().map(org.deltafi.core.domain.generated.types.TransformActionConfiguration::getProduces).collect(Collectors.toSet());

        errors.addAll(validateTransformsAreReachable(transformActionConfigurations, producedTypes, flowType));
        errors.addAll(validateLoadActionsAreReachable(loadActionConfigurations, producedTypes, flowType));

        return errors;
    }

    List<String> validateTransformsAreReachable(List<TransformActionConfiguration> transformActionConfigurations, Set<String> producedTypes, String flowType) {
        if (transformActionConfigurations.isEmpty()) {
            return emptyList();
        }

        List<String> errors = new ArrayList<>();

        boolean handlesIngressType = false;
        for (TransformActionConfiguration transformActionConfiguration: transformActionConfigurations) {
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

    List<String> validateLoadActionsAreReachable(List<LoadActionConfiguration> loadActionConfigurations, Set<String> producedTypes, String flowType) {
        List<String> errors = new ArrayList<>();

        // If produceTypes is populated then assume a TransformAction handles the ingress flow type. Otherwise, a LoadAction needs to consume the flowType
        boolean isIngressTypeHandled = !producedTypes.isEmpty();
        for (LoadActionConfiguration loadActionConfiguration: loadActionConfigurations) {
            String consumes = loadActionConfiguration.getConsumes();
            if (flowType.equals(consumes)) {
                isIngressTypeHandled = true;
            } else if (!producedTypes.contains(loadActionConfiguration.getConsumes())) {
                errors.add("Load Action named: " + loadActionConfiguration.getName() + " consumes: " + loadActionConfiguration.getConsumes() + " which isn't produced in this flow");
            }
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

    List<LoadActionConfiguration> getLoadConfigs(DeltafiRuntimeConfiguration deltafiRuntimeConfiguration, IngressFlowConfiguration ingressFlowConfiguration, List<String> errors) {
        if (isNull(ingressFlowConfiguration.getLoadActions()) || ingressFlowConfiguration.getLoadActions().isEmpty()) {
            errors.add("Required property loadActions must be set to a non-empty list");
            return emptyList();
        }
        List<LoadActionConfiguration> loadActionConfigurations = new ArrayList<>();
        for (String loadActionOrGroup : ingressFlowConfiguration.getLoadActions()) {
            List<String> actionNames = loadActionOrGroup.endsWith("Group") ? getLoadConfigsInGroup(deltafiRuntimeConfiguration, loadActionOrGroup, errors) : List.of(loadActionOrGroup);
            for (String name : actionNames) {
                LoadActionConfiguration loadActionConfiguration = deltafiRuntimeConfiguration.getLoadActions().get(name);
                if (isNull(loadActionConfiguration)) {
                    errors.add(RuntimeConfigValidator.referenceError("Load Action", name));
                } else {
                    loadActionConfigurations.add(loadActionConfiguration);
                }
            }
        }
        return loadActionConfigurations;
    }

    List<String> getLoadConfigsInGroup(DeltafiRuntimeConfiguration deltafiRuntimeConfiguration, String loadGroupName, List<String> errors) {
        LoadActionGroupConfiguration groupConfig = deltafiRuntimeConfiguration.getLoadGroups().get(loadGroupName);
        if (isNull(groupConfig)) {
            errors.add(RuntimeConfigValidator.referenceError("Load Action Group", loadGroupName));
        }

        return isNull(groupConfig) ? emptyList() : groupConfig.getLoadActions();
    }
}
