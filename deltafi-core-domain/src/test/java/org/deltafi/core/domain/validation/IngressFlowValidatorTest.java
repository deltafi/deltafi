package org.deltafi.core.domain.validation;

import org.deltafi.core.domain.configuration.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;

class IngressFlowValidatorTest {

    IngressFlowValidator ingressFlowValidator = new IngressFlowValidator();

    @Test
    void validate() {
        Optional<String> error = ingressFlowValidator.validate(new DeltafiRuntimeConfiguration(), new IngressFlowConfiguration());
        assertThat(error)
                .isPresent()
                .contains("Ingress Flow Configuration: IngressFlowConfiguration{name='null',apiVersion='null',type='null',transformActions='[]',loadActions='[]'} has the following errors: \n" +
                        "Required property type is not set; Required property name is not set; Required property loadActions must be set to a non-empty list");
    }

    @Test
    void runValidateIngressFlow() {
        List<String> errors = ingressFlowValidator.runValidateIngressFlow(new DeltafiRuntimeConfiguration(), new IngressFlowConfiguration());
        assertThat(errors)
                .hasSize(3)
                .contains("Required property type is not set")
                .contains("Required property name is not set")
                .contains("Required property loadActions must be set to a non-empty list");
    }

    @Test
    void runValidateIngressFlow_allValid() {
        IngressFlowConfiguration ingressConfig = new IngressFlowConfiguration();
        ingressConfig.setName("ingress");
        ingressConfig.setType("json");
        ingressConfig.setLoadActions(List.of("l1"));

        DeltafiRuntimeConfiguration runtimeConfig = new DeltafiRuntimeConfiguration();

        LoadActionConfiguration l1 = new LoadActionConfiguration();
        l1.setConsumes("json");
        runtimeConfig.getLoadActions().put("l1", l1);
        List<String> errors = ingressFlowValidator.runValidateIngressFlow(runtimeConfig, ingressConfig);

        assertThat(errors).isEmpty();
    }

    @Test
    void validateTransformsAreReachable_valid() {
        Set<String> producedTypes = Set.of("b");
        TransformActionConfiguration flowHandler = new TransformActionConfiguration();
        flowHandler.setConsumes("flowType");
        flowHandler.setProduces("b");

        List<TransformActionConfiguration> transformActionConfigurations = of(flowHandler);
        List<String> errors = ingressFlowValidator.validateTransformsAreReachable(transformActionConfigurations, producedTypes, "flowType");
        assertThat(errors).isEmpty();
    }

    @Test
    void validateTransformsAreReachable_noTransforms() {
        assertThat(ingressFlowValidator.validateTransformsAreReachable(emptyList(), emptySet(), "flowType")).isEmpty();
    }

    @Test
    void validateTransformsAreReachable_flowTypeNotHandled() {
        Set<String> producedTypes = Set.of("b", "c");
        TransformActionConfiguration flowHandler = new TransformActionConfiguration();
        flowHandler.setConsumes("b");
        flowHandler.setProduces("c");

        List<TransformActionConfiguration> transformActionConfigurations = of(flowHandler);
        List<String> errors = ingressFlowValidator.validateTransformsAreReachable(transformActionConfigurations, producedTypes, "flowType");
        assertThat(errors).hasSize(1).contains("None of the configured TransformActions in this flow consume the ingress flow type: flowType");
    }

    @Test
    void validateTransformsAreReachable_unreachableTransform() {
        Set<String> producedTypes = Set.of("c");
        TransformActionConfiguration flowHandler = new TransformActionConfiguration();
        flowHandler.setConsumes("flowType");
        flowHandler.setProduces("c");

        TransformActionConfiguration unreachable = new TransformActionConfiguration();
        unreachable.setName("unreachable");
        unreachable.setConsumes("a");
        unreachable.setProduces("d");

        List<TransformActionConfiguration> transformActionConfigurations = of(flowHandler, unreachable);
        List<String> errors = ingressFlowValidator.validateTransformsAreReachable(transformActionConfigurations, producedTypes, "flowType");
        assertThat(errors).hasSize(1).contains("Transform Action named: unreachable consumes: a which is not produced in this flow");
    }

    @Test
    void validateLoadActionsAreReachable_valid() {
        LoadActionConfiguration config = new LoadActionConfiguration();
        config.setConsumes("flowType");

        List<String> errors = ingressFlowValidator.validateLoadActionsAreReachable(of(config), emptySet(), "flowType");
        assertThat(errors).isEmpty();
    }

    @Test
    void validateLoadActionsAreReachable_flowTypeNotConsumed() {
        LoadActionConfiguration config = new LoadActionConfiguration();
        config.setConsumes("other");
        config.setName("loader");

        List<String> errors = ingressFlowValidator.validateLoadActionsAreReachable(of(config), emptySet(), "flowType");
        assertThat(errors).hasSize(2)
                .contains("Load Action named: loader consumes: other which isn't produced in this flow")
                .contains("None of the configured Load Actions in this flow consume the ingress flow type: flowType");
    }

    @Test
    void getTransformConfigs_valid() {
        List<String> errors = new ArrayList<>();

        IngressFlowConfiguration ingressFlowConfiguration = new IngressFlowConfiguration();
        ingressFlowConfiguration.getTransformActions().add("transformer");

        DeltafiRuntimeConfiguration runtimeConfiguration = new DeltafiRuntimeConfiguration();
        runtimeConfiguration.getTransformActions().put("transformer", new TransformActionConfiguration());

        List<TransformActionConfiguration> found = ingressFlowValidator.getTransformConfigs(runtimeConfiguration, ingressFlowConfiguration, errors);
        assertThat(found).hasSize(1);

        assertThat(errors).isEmpty();
    }

    @Test
    void getTransformConfigs_missing() {
        List<String> errors = new ArrayList<>();

        IngressFlowConfiguration ingressFlowConfiguration = new IngressFlowConfiguration();
        ingressFlowConfiguration.getTransformActions().add("transformer");

        List<TransformActionConfiguration> found = ingressFlowValidator.getTransformConfigs(new DeltafiRuntimeConfiguration(), ingressFlowConfiguration, errors);
        assertThat(found).isEmpty();
        assertThat(errors).hasSize(1).contains("The referenced Transform Action named: transformer was not found");
    }

    @Test
    void getLoadConfigs_valid() {
        List<String> errors = new ArrayList<>();

        IngressFlowConfiguration ingressFlowConfiguration = new IngressFlowConfiguration();
        ingressFlowConfiguration.getLoadActions().add("loader");

        DeltafiRuntimeConfiguration runtimeConfiguration = new DeltafiRuntimeConfiguration();
        runtimeConfiguration.getLoadActions().put("loader", new LoadActionConfiguration());

        List<LoadActionConfiguration> found = ingressFlowValidator.getLoadConfigs(runtimeConfiguration, ingressFlowConfiguration, errors);
        assertThat(found).hasSize(1);

        assertThat(errors).isEmpty();
    }

    @Test
    void getLoadConfigs_missing() {
        List<String> errors = new ArrayList<>();

        IngressFlowConfiguration ingressFlowConfiguration = new IngressFlowConfiguration();
        ingressFlowConfiguration.getLoadActions().add("loader");

        List<LoadActionConfiguration> found = ingressFlowValidator.getLoadConfigs(new DeltafiRuntimeConfiguration(), ingressFlowConfiguration, errors);
        assertThat(found).isEmpty();
        assertThat(errors).hasSize(1).contains("The referenced Load Action named: loader was not found");
    }
}
