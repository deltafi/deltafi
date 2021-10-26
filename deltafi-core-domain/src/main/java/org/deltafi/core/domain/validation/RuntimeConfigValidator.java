package org.deltafi.core.domain.validation;

import org.deltafi.core.domain.configuration.DeltaFiConfiguration;
import org.deltafi.core.domain.configuration.DeltafiRuntimeConfiguration;

import java.util.Optional;

public interface RuntimeConfigValidator <C extends DeltaFiConfiguration> {

    Optional<String> validate(DeltafiRuntimeConfiguration deltafiRuntimeConfiguration, C config);

    String getName();

    static String referenceError(String type, String name) {
        return "The referenced " + type + " named: " + name + " was not found";
    }
}
