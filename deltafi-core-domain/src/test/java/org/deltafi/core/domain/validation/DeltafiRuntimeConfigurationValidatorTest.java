package org.deltafi.core.domain.validation;

import org.deltafi.core.domain.configuration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DeltafiRuntimeConfigurationValidatorTest {

    @InjectMocks
    DeltafiRuntimeConfigurationValidator validator;

    @Mock
    ActionConfigurationValidator actionConfigurationValidator;
    @Mock
    IngressFlowValidator ingressFlowValidator;
    @Mock
    EgressFlowValidator egressFlowValidator;

    @Test
    void validate() {
        DeltafiRuntimeConfiguration runtimeConfiguration = new DeltafiRuntimeConfiguration();

        LoadActionConfiguration loadAction = new LoadActionConfiguration();
        loadAction.setName("l");

        IngressFlowConfiguration ingress = new IngressFlowConfiguration();
        ingress.setName("i");

        EgressFlowConfiguration egress = new EgressFlowConfiguration();
        egress.setName("e");

        runtimeConfiguration.getLoadActions().put("l", loadAction);
        runtimeConfiguration.getIngressFlows().put("i", ingress);
        runtimeConfiguration.getEgressFlows().put("e", egress);

        validator.validate(runtimeConfiguration);

        Mockito.verify(actionConfigurationValidator, Mockito.times(1)).validateActionConfiguration(Mockito.any());
        Mockito.verify(ingressFlowValidator, Mockito.times(1)).validate(Mockito.eq(runtimeConfiguration), Mockito.any());
        Mockito.verify(egressFlowValidator, Mockito.times(1)).validate(Mockito.eq(runtimeConfiguration), Mockito.any());
    }

    @Test
    void validate_duplicateActionNames() {
        DeltafiRuntimeConfiguration runtimeConfiguration = new DeltafiRuntimeConfiguration();

        LoadActionConfiguration loadAction = new LoadActionConfiguration();
        loadAction.setName("action");

        TransformActionConfiguration transform = new TransformActionConfiguration();
        transform.setName("action");

        ValidateActionConfiguration validate = new ValidateActionConfiguration();
        validate.setName("validator");

        runtimeConfiguration.getLoadActions().put("action", loadAction);
        runtimeConfiguration.getTransformActions().put("action", transform);
        runtimeConfiguration.getValidateActions().put("validate", validate);

        List<String> errors = validator.validate(runtimeConfiguration);
        assertThat(errors)
                .hasSize(2)
                .contains("Action name: action is duplicated")
                .contains("Action name: validator did not match the key validate");
    }

    @Test
    void validate_duplicateFlowName() {
        Mockito.when(ingressFlowValidator.getName()).thenCallRealMethod();
        DeltafiRuntimeConfiguration runtimeConfiguration = new DeltafiRuntimeConfiguration();

        IngressFlowConfiguration ingress = new IngressFlowConfiguration();
        ingress.setName("i");

        IngressFlowConfiguration ingress2 = new IngressFlowConfiguration();
        ingress2.setName("i");

        runtimeConfiguration.getIngressFlows().put("i", ingress);
        runtimeConfiguration.getIngressFlows().put("i2", ingress2);

        List<String> errors = validator.validate(runtimeConfiguration);
        assertThat(errors)
                .hasSize(2)
                .contains("Ingress Flow name: i is duplicated")
                .contains("Ingress Flow name: i did not match the key i2");
    }
}
