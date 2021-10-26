package org.deltafi.core.domain.validation;

import org.deltafi.core.domain.configuration.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
public class EgressFlowValidator implements RuntimeConfigValidator<EgressFlowConfiguration> {

    @Override
    public Optional<String> validate(DeltafiRuntimeConfiguration configuration, EgressFlowConfiguration egressFlowConfiguration) {
        List<String> errors = runValidateEgressFlow(configuration, egressFlowConfiguration);
        if (!errors.isEmpty()) {
            return Optional.of("Egress Flow Configuration: " + egressFlowConfiguration + " has the following errors: \n" + String.join("; ", errors));
        }

        return Optional.empty();
    }

    @Override
    public String getName() {
        return "Egress Flow";
    }

    List<String> runValidateEgressFlow(DeltafiRuntimeConfiguration config, EgressFlowConfiguration egressFlowConfiguration) {
        List<String> errors = new ArrayList<>();

        getFormatActionErrors(config, egressFlowConfiguration).ifPresent(errors::add);
        errors.addAll(getValidateActionErrors(config, egressFlowConfiguration));

        if (isBlank(egressFlowConfiguration.getName())) {
            errors.add("Required property name is not set");
        }

        if (isBlank(egressFlowConfiguration.getEgressAction())) {
            errors.add("Required property egressAction is not set");
        }

        errors.addAll(missingIngressCheck(config, egressFlowConfiguration.getExcludeIngressFlows()));
        errors.addAll(missingIngressCheck(config, egressFlowConfiguration.getIncludeIngressFlows()));
        errors.addAll(excludedAndIncluded(egressFlowConfiguration));

        return errors;
    }

    List<String> missingIngressCheck(DeltafiRuntimeConfiguration deltafiRuntimeConfiguration, List<String> ingressFlows) {
        if (isNull(ingressFlows)) {
            return emptyList();
        }

        return ingressFlows.stream()
                .filter(flowName -> isIngressFlowMissing(deltafiRuntimeConfiguration, flowName))
                .map(flowName -> RuntimeConfigValidator.referenceError("Ingress Flow", flowName))
                .collect(Collectors.toList());
    }

    List<String> excludedAndIncluded(EgressFlowConfiguration egressFlowConfiguration) {
        if (nonNull(egressFlowConfiguration.getExcludeIngressFlows()) && nonNull(egressFlowConfiguration.getIncludeIngressFlows())) {
            return egressFlowConfiguration.getExcludeIngressFlows().stream()
                    .filter(flowName -> egressFlowConfiguration.getIncludeIngressFlows().contains(flowName))
                    .map(flowName -> "Ingress Flow " + flowName + " is both included and excluded")
                    .collect(Collectors.toList());
        }

        return emptyList();
    }


    Optional<String> getFormatActionErrors(DeltafiRuntimeConfiguration deltafiRuntimeConfiguration, EgressFlowConfiguration egressFlowConfiguration) {
        String formatAction = egressFlowConfiguration.getFormatAction();

        if (isBlank(formatAction)) {
            return Optional.of("Required property formatAction is not set");
        } else if (!deltafiRuntimeConfiguration.getFormatActions().containsKey(formatAction)) {
            return Optional.of(RuntimeConfigValidator.referenceError("Format Action", formatAction));
        }

        return Optional.empty();
    }

    List<String> getValidateActionErrors(DeltafiRuntimeConfiguration config, EgressFlowConfiguration egressFlowConfiguration) {
        if (isNull(egressFlowConfiguration.getValidateActions())) {
            return emptyList();
        }

        return egressFlowConfiguration.getValidateActions().stream()
                .filter(validateAction -> isValidateActionMissing(config, validateAction))
                .map(name -> RuntimeConfigValidator.referenceError("Validate Action", name))
                .collect(Collectors.toList());
    }

    boolean isIngressFlowMissing(DeltafiRuntimeConfiguration runtimeConfiguration, String flowName) {
        return !runtimeConfiguration.getIngressFlows().containsKey(flowName);
    }

    boolean isValidateActionMissing(DeltafiRuntimeConfiguration runtimeConfiguration, String actionName) {
        return !runtimeConfiguration.getValidateActions().containsKey(actionName);
    }

}
