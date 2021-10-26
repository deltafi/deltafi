package org.deltafi.core.domain.validation;

import org.deltafi.core.domain.configuration.DeltafiRuntimeConfiguration;
import org.deltafi.core.domain.configuration.LoadActionGroupConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoadActionGroupValidatorTest {

    LoadActionGroupValidator loadActionGroupValidator = new LoadActionGroupValidator();

    @Test
    void validate() {
        DeltafiRuntimeConfiguration runtimeConfiguration = new DeltafiRuntimeConfiguration();
        LoadActionGroupConfiguration loadGroupConfig = new LoadActionGroupConfiguration();
        loadGroupConfig.setName("test");
        loadGroupConfig.setLoadActions(List.of("a"));
        assertThat(loadActionGroupValidator.validate(runtimeConfiguration, loadGroupConfig))
                .isPresent().contains("Load Action Group Configuration: LoadActionGroupConfiguration{name='test',created='null',modified='null',apiVersion='null',loadActions='[a]'} has the following errors: \n" +
                        "The referenced Load Action named: a was not found");
    }

    @Test
    void validate_nullAndEmpty() {
        DeltafiRuntimeConfiguration runtimeConfiguration = new DeltafiRuntimeConfiguration();
        LoadActionGroupConfiguration loadGroupConfig = new LoadActionGroupConfiguration();
        loadGroupConfig.setLoadActions(null);
        assertThat(loadActionGroupValidator.validate(runtimeConfiguration, loadGroupConfig)).isEmpty();

        loadGroupConfig.setLoadActions(Collections.emptyList());
        assertThat(loadActionGroupValidator.validate(runtimeConfiguration, loadGroupConfig)).isEmpty();
    }
}