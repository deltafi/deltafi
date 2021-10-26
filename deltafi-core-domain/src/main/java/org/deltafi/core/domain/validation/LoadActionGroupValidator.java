package org.deltafi.core.domain.validation;

import org.deltafi.core.domain.configuration.DeltafiRuntimeConfiguration;
import org.deltafi.core.domain.configuration.LoadActionGroupConfiguration;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
public class LoadActionGroupValidator implements RuntimeConfigValidator<LoadActionGroupConfiguration> {

    @Override
    public Optional<String> validate(DeltafiRuntimeConfiguration configuration, LoadActionGroupConfiguration loadActionGroupConfiguration) {
        List<String> errors = runValidateLoadActionGroup(configuration, loadActionGroupConfiguration);
        if (!errors.isEmpty()) {
            return Optional.of("Load Action Group Configuration: " + loadActionGroupConfiguration + " has the following errors: \n" + String.join("; ", errors));
        }

        return Optional.empty();
    }

    public List<String> runValidateLoadActionGroup(DeltafiRuntimeConfiguration deltafiRuntimeConfiguration, LoadActionGroupConfiguration loadActionGroupConfiguration) {
        if (isNull(loadActionGroupConfiguration.getLoadActions())) {
            return Collections.emptyList();
        }

        return loadActionGroupConfiguration.getLoadActions().stream()
                .filter(referencedAction -> missingLoadActionConfiguration(deltafiRuntimeConfiguration, referencedAction))
                .map(name -> RuntimeConfigValidator.referenceError("Load Action", name))
                .collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return "Load Action Group";
    }

    boolean missingLoadActionConfiguration(DeltafiRuntimeConfiguration runtimeConfiguration, String loadActionName) {
        return !runtimeConfiguration.getLoadActions().containsKey(loadActionName);
    }

}
