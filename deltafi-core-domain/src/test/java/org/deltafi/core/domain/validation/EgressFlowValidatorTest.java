package org.deltafi.core.domain.validation;

import org.deltafi.core.domain.configuration.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;

class EgressFlowValidatorTest {

    EgressFlowValidator egressFlowValidator = new EgressFlowValidator();

    @Test
    void validate() {
        Optional<String> error = egressFlowValidator.validate(new DeltafiRuntimeConfiguration(), new EgressFlowConfiguration());
        assertThat(error)
                .isPresent()
                .contains("Egress Flow Configuration: EgressFlowConfiguration{name='null',created='null',modified='null',apiVersion='null',egressAction='null',formatAction='null',enrichActions='[]',validateActions='[]',includeIngressFlows='[]',excludeIngressFlows='[]'} has the following errors: \n" +
                        "Required property egressAction is not set; Required property formatAction is not set; Required property name is not set");
    }

    @Test
    void runValidateEgressFlow() {
        List<String> errors = egressFlowValidator.runValidateEgressFlow(new DeltafiRuntimeConfiguration(), new EgressFlowConfiguration());
        assertThat(errors)
                .hasSize(3)
                .contains("Required property name is not set")
                .contains("Required property egressAction is not set")
                .contains("Required property formatAction is not set");
    }

    @Test
    void missingIngressCheck() {
        DeltafiRuntimeConfiguration config = new DeltafiRuntimeConfiguration();
        config.getIngressFlows().put("a", new IngressFlowConfiguration());
        config.getIngressFlows().put("b", new IngressFlowConfiguration());
        List<String> errors = egressFlowValidator.missingIngressCheck(config, of("c"));

        assertThat(errors).hasSize(1).contains("The referenced Ingress Flow named: c was not found");

        assertThat(egressFlowValidator.missingIngressCheck(config, of("a"))).isEmpty();
        assertThat(egressFlowValidator.missingIngressCheck(config, null)).isEmpty();
    }

    @Test
    void excludedAndIncluded() {
        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setIncludeIngressFlows(of("a", "b"));
        config.setExcludeIngressFlows(of("b", "c"));
        List<String> errors = egressFlowValidator.excludedAndIncluded(config);

        assertThat(errors).hasSize(1).contains("Ingress Flow b is both included and excluded");

        config.setIncludeIngressFlows(null);
        assertThat(egressFlowValidator.excludedAndIncluded(config)).isEmpty();
    }

    @Test
    void getEgressActionErrors() {
        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setEgressAction("Egressor");

        DeltafiRuntimeConfiguration deltafiRuntimeConfiguration = new DeltafiRuntimeConfiguration();

        Optional<String> error = egressFlowValidator.getEgressActionErrors(deltafiRuntimeConfiguration, config);

        assertThat(error).isPresent().contains("The referenced Egress Action named: Egressor was not found");

        deltafiRuntimeConfiguration.getEgressActions().put("Egressor", new EgressActionConfiguration());
        assertThat(egressFlowValidator.getEgressActionErrors(deltafiRuntimeConfiguration, config)).isEmpty();
    }

    @Test
    void getFormatActionErrors() {
        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setFormatAction("Formatter");

        DeltafiRuntimeConfiguration deltafiRuntimeConfiguration = new DeltafiRuntimeConfiguration();

        Optional<String> error = egressFlowValidator.getFormatActionErrors(deltafiRuntimeConfiguration, config);

        assertThat(error).isPresent().contains("The referenced Format Action named: Formatter was not found");

        deltafiRuntimeConfiguration.getFormatActions().put("Formatter", new FormatActionConfiguration());
        assertThat(egressFlowValidator.getFormatActionErrors(deltafiRuntimeConfiguration, config)).isEmpty();
    }

    @Test
    void getEnrichActionErrors() {
        DeltafiRuntimeConfiguration runtimeConfiguration = new DeltafiRuntimeConfiguration();
        runtimeConfiguration.getEnrichActions().put("a", new EnrichActionConfiguration());

        EgressFlowConfiguration flowConfig = new EgressFlowConfiguration();
        flowConfig.getEnrichActions().add("a");

        assertThat(egressFlowValidator.getEnrichActionErrors(runtimeConfiguration, flowConfig)).isEmpty();

        flowConfig.getEnrichActions().add("b");
        assertThat(egressFlowValidator.getEnrichActionErrors(runtimeConfiguration, flowConfig)).hasSize(1).contains("The referenced Enrich Action named: b was not found");

        flowConfig.setEnrichActions(null);
        assertThat(egressFlowValidator.getEnrichActionErrors(runtimeConfiguration, flowConfig)).isEmpty();
    }

    @Test
    void getValidateActionErrors() {
        DeltafiRuntimeConfiguration runtimeConfiguration = new DeltafiRuntimeConfiguration();
        runtimeConfiguration.getValidateActions().put("a", new ValidateActionConfiguration());

        EgressFlowConfiguration flowConfig = new EgressFlowConfiguration();
        flowConfig.getValidateActions().add("a");

        assertThat(egressFlowValidator.getValidateActionErrors(runtimeConfiguration, flowConfig)).isEmpty();

        flowConfig.getValidateActions().add("b");
        assertThat(egressFlowValidator.getValidateActionErrors(runtimeConfiguration, flowConfig)).hasSize(1).contains("The referenced Validate Action named: b was not found");

        flowConfig.setValidateActions(null);
        assertThat(egressFlowValidator.getValidateActionErrors(runtimeConfiguration, flowConfig)).isEmpty();
    }

}
