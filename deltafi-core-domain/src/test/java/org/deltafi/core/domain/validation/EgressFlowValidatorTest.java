/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.domain.validation;

import org.assertj.core.api.Assertions;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.EgressActionConfiguration;
import org.deltafi.core.domain.configuration.FormatActionConfiguration;
import org.deltafi.core.domain.configuration.ValidateActionConfiguration;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowErrorType;
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

        FormatActionConfiguration format = new FormatActionConfiguration();
        format.setName("format");

        ValidateActionConfiguration validate1 = new ValidateActionConfiguration();
        validate1.setName("validate1");

        ValidateActionConfiguration validate2 = new ValidateActionConfiguration();
        validate2.setName("validate2");

        EgressActionConfiguration egress = new EgressActionConfiguration();
        egress.setName("egress");

        egressFlow.setFormatAction(format);
        egressFlow.setValidateActions(List.of(validate1, validate2));
        egressFlow.setEgressAction(egress);

        List<FlowConfigError> errors = egressFlowValidator.validate(egressFlow);

        Mockito.verify(schemaCompliancyValidator, Mockito.times(4)).validate(actionConfigCaptor.capture());

        List<ActionConfiguration> validatedActions = actionConfigCaptor.getAllValues();
        Assertions.assertThat(validatedActions).hasSize(4)
                .contains(format)
                .contains(validate1)
                .contains(validate2)
                .contains(egress);

        Assertions.assertThat(errors).isEmpty();
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

        List<FlowConfigError> errors = egressFlowValidator.validate(egressFlow);
        Assertions.assertThat(errors).hasSize(1).contains(expected);
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