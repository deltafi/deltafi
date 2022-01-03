package org.deltafi.core.domain.validation;

import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.DeltaFiConfiguration;
import org.deltafi.core.domain.configuration.DeltafiRuntimeConfiguration;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DeltafiRuntimeConfigurationValidator {

    private final ActionConfigurationValidator actionConfigurationValidator;
    private final IngressFlowValidator ingressFlowValidator;
    private final EgressFlowValidator egressFlowValidator;

    public DeltafiRuntimeConfigurationValidator(ActionConfigurationValidator actionConfigurationValidator,
                                                IngressFlowValidator ingressFlowValidator, EgressFlowValidator egressFlowValidator) {
        this.actionConfigurationValidator = actionConfigurationValidator;
        this.ingressFlowValidator = ingressFlowValidator;
        this.egressFlowValidator = egressFlowValidator;
    }

    public List<String> validate(DeltafiRuntimeConfiguration runtimeConfiguration) {
        List<String> errors = validateActionConfigurations(runtimeConfiguration);

        // Action errors need to be fixed before we can verify flows
        if (errors.isEmpty()) {
            errors.addAll(validateIngressFlows(runtimeConfiguration));
            errors.addAll(validateEgressFlows(runtimeConfiguration));
        }

       return errors;
    }

    List<String> validateActionConfigurations(DeltafiRuntimeConfiguration runtimeConfiguration) {
        List<String> errors = new ArrayList<>();
        Set<String> actionNames = new HashSet<>();

        runtimeConfiguration.actionMaps().map(Map::entrySet).flatMap(Set::stream).forEach(entry -> {
            String key = entry.getKey();
            ActionConfiguration config = entry.getValue();

            if (!key.equals(config.getName())) {
                errors.add("Action name: " + config.getName() + " did not match the key " + key);
            }

            if (actionNames.contains(config.getName())) {
                errors.add("Action name: " + config.getName() + " is duplicated");
            }
            actionNames.add(config.getName());
            actionConfigurationValidator.validateActionConfiguration(config).ifPresent(errors::add);
        });

        return errors;
    }

    List<String> validateIngressFlows(DeltafiRuntimeConfiguration runtimeConfiguration) {
        return validateDeltafiConfigs(runtimeConfiguration, runtimeConfiguration.getIngressFlows(), ingressFlowValidator);
    }

    List<String> validateEgressFlows(DeltafiRuntimeConfiguration runtimeConfiguration) {
        return validateDeltafiConfigs(runtimeConfiguration, runtimeConfiguration.getEgressFlows(), egressFlowValidator);
    }

    <C extends DeltaFiConfiguration> List<String> validateDeltafiConfigs(DeltafiRuntimeConfiguration runtimeConfiguration, Map<String, C> configMap, RuntimeConfigValidator<C> validator) {
        List<String> errors = new ArrayList<>();
        Set<String> flowNames = new HashSet<>();
        configMap.forEach((key, config) -> {
            if (!key.equals(config.getName())) {
                errors.add(validator.getName() + " name: " + config.getName() + " did not match the key " + key);
            }

            if (flowNames.contains(config.getName())) {
                errors.add(validator.getName() + " name: " + config.getName() + " is duplicated");
            }
            flowNames.add(config.getName());
            Optional<String> optionalError = validator.validate(runtimeConfiguration, config);
            optionalError.ifPresent(errors::add);
        });
        return errors;
    }

}
