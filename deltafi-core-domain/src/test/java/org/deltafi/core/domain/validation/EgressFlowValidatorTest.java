package org.deltafi.core.domain.validation;

import org.assertj.core.api.Assertions;
import org.deltafi.core.domain.configuration.*;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowErrorType;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.FlowStatus;
import org.deltafi.core.domain.types.EgressFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class EgressFlowValidatorTest {

    @InjectMocks
    EgressFlowValidator egressFlowValidator;

    @Mock
    SchemaCompliancyValidator schemaCompliancyValidator;

    @Captor
    ArgumentCaptor<ActionConfiguration> actionConfigCaptor;


    @Test
    void validate_noErrors() {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setName("egressFlow");

        EnrichActionConfiguration enrich1 = new EnrichActionConfiguration();
        enrich1.setName("enrich1");

        EnrichActionConfiguration enrich2 = new EnrichActionConfiguration();
        enrich2.setName("enrich2");

        FormatActionConfiguration format = new FormatActionConfiguration();
        format.setName("format");

        ValidateActionConfiguration validate1 = new ValidateActionConfiguration();
        validate1.setName("validate1");

        ValidateActionConfiguration validate2 = new ValidateActionConfiguration();
        validate2.setName("validate2");

        EgressActionConfiguration egress = new EgressActionConfiguration();
        egress.setName("egress");

        egressFlow.setEnrichActions(List.of(enrich1, enrich2));
        egressFlow.setFormatAction(format);
        egressFlow.setValidateActions(List.of(validate1, validate2));
        egressFlow.setEgressAction(egress);

        egressFlowValidator.validate(egressFlow);

        Mockito.verify(schemaCompliancyValidator, Mockito.times(6)).validate(actionConfigCaptor.capture());

        List<ActionConfiguration> validatedActions = actionConfigCaptor.getAllValues();
        Assertions.assertThat(validatedActions).hasSize(6)
                .contains(enrich1)
                .contains(enrich2)
                .contains(format)
                .contains(validate1)
                .contains(validate2)
                .contains(egress);

        Assertions.assertThat(egressFlow.getFlowStatus().getState()).isEqualTo(FlowState.STOPPED);
        Assertions.assertThat(egressFlow.getFlowStatus().getErrors()).isEmpty();
    }

    @Test
    void validate_createErrors() {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setName("egressFlow");

        FormatActionConfiguration format = new FormatActionConfiguration();
        format.setName("fail");
        egressFlow.setFormatAction(format);
        egressFlow.setEgressAction(new EgressActionConfiguration());

        FlowConfigError expected = expectedError();
        Mockito.when(schemaCompliancyValidator.validate(Mockito.argThat((action) -> "fail".equals(action.getName()))))
                        .thenReturn(List.of(expected));

        egressFlowValidator.validate(egressFlow);

        FlowStatus status = egressFlow.getFlowStatus();
        Assertions.assertThat(status.getState()).isEqualTo(FlowState.INVALID);
        Assertions.assertThat(status.getErrors()).hasSize(1).contains(expected);
    }

    @Test
    void duplicateActionNameErrors() {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setName("egressFlow");

        EnrichActionConfiguration enrich1 = new EnrichActionConfiguration();
        enrich1.setName("action");
        enrich1.setType("org.deltafi.enrich.Action1");

        EnrichActionConfiguration enrich2 = new EnrichActionConfiguration();
        enrich2.setName("enrich");
        enrich2.setType("org.deltafi.enrich.Action2");

        EnrichActionConfiguration enrich3 = new EnrichActionConfiguration();
        enrich3.setName("enrich");
        enrich3.setType("org.deltafi.enrich.Action3");

        FormatActionConfiguration format = new FormatActionConfiguration();
        format.setName("action");
        format.setType("org.deltafi.format.Action");

        ValidateActionConfiguration validate1 = new ValidateActionConfiguration();
        validate1.setName("action");
        validate1.setType("org.deltafi.validate.Action1");

        ValidateActionConfiguration validate2 = new ValidateActionConfiguration();
        validate2.setName("validate");
        validate2.setType("org.deltafi.validate.Action2");

        ValidateActionConfiguration validate3 = new ValidateActionConfiguration();
        validate3.setName("validate");
        validate3.setType("org.deltafi.validate.Action3");

        EgressActionConfiguration egress = new EgressActionConfiguration();
        egress.setName("action");
        egress.setType("org.deltafi.egress.Action");

        egressFlow.setEnrichActions(List.of(enrich1, enrich2, enrich3));
        egressFlow.setFormatAction(format);
        egressFlow.setValidateActions(List.of(validate1, validate2, validate3));
        egressFlow.setEgressAction(egress);

        egressFlowValidator.validate(egressFlow);

        Assertions.assertThat(egressFlow.getFlowStatus().getState()).isEqualTo(FlowState.INVALID);
        Assertions.assertThat(egressFlow.getFlowStatus().getErrors())
                .hasSize(3)
                .contains(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG).configName("action")
                        .message("The action name: action is duplicated for the following action types: org.deltafi.format.Action, org.deltafi.egress.Action, org.deltafi.enrich.Action1, org.deltafi.validate.Action1").build())
                .contains(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG).configName("enrich")
                        .message("The action name: enrich is duplicated for the following action types: org.deltafi.enrich.Action2, org.deltafi.enrich.Action3").build())
                .contains(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG).configName("validate")
                        .message("The action name: validate is duplicated for the following action types: org.deltafi.validate.Action2, org.deltafi.validate.Action3").build());
    }

    @Test
    void testValidateActions_null() {
        Assertions.assertThat(egressFlowValidator.validateActions(null)).isEmpty();
    }

    @Test
    void testExcludedAndIncluded() {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setName("egressFlowName");
        egressFlow.setIncludeIngressFlows(List.of("passthrough", "dupe2", "included"));
        egressFlow.setExcludeIngressFlows(List.of("passthrough", "dupe2", "excluded"));
        List<FlowConfigError> errors = egressFlowValidator.excludedAndIncluded(egressFlow);
        Assertions.assertThat(errors).hasSize(2);

        Assertions.assertThat(errors.get(0).getErrorType()).isEqualTo(FlowErrorType.INVALID_CONFIG);
        Assertions.assertThat(errors.get(0).getConfigName()).isEqualTo("egressFlowName");
        Assertions.assertThat(errors.get(0).getMessage()).isEqualTo("Flow: passthrough is both included and excluded");
        Assertions.assertThat(errors.get(1).getErrorType()).isEqualTo(FlowErrorType.INVALID_CONFIG);
        Assertions.assertThat(errors.get(1).getConfigName()).isEqualTo("egressFlowName");
        Assertions.assertThat(errors.get(1).getMessage()).isEqualTo("Flow: dupe2 is both included and excluded");
    }

    @Test
    void testExcludedAndIncluded_null() {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setName("egressFlowName");
        egressFlow.setIncludeIngressFlows(null);
        egressFlow.setExcludeIngressFlows(List.of("passthrough", "dupe2", "excluded"));
        Assertions.assertThat(egressFlowValidator.excludedAndIncluded(egressFlow)).isEmpty();
    }

    FlowConfigError expectedError() {
        FlowConfigError actionConfigError = new FlowConfigError();
        actionConfigError.setConfigName("brokenAction");
        actionConfigError.setErrorType(FlowErrorType.UNREGISTERED_ACTION);
        actionConfigError.setMessage("Action: brokenAction has not been registered with the system");
        return actionConfigError;
    }

}
