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
package org.deltafi.core.validation;

import org.assertj.core.api.Assertions;
import org.deltafi.core.configuration.ActionConfiguration;
import org.deltafi.core.configuration.LoadActionConfiguration;
import org.deltafi.core.configuration.TransformActionConfiguration;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.types.IngressFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class IngressFlowValidatorTest {

    @InjectMocks
    IngressFlowValidator ingressFlowValidator;

    @Mock
    SchemaComplianceValidator schemaComplianceValidator;

    @Captor
    ArgumentCaptor<ActionConfiguration> actionConfigCaptor;

    @Test
    void validate() {
        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName("ingress");
        TransformActionConfiguration transform1 = new TransformActionConfiguration();
        transform1.setName("transform1");
        TransformActionConfiguration transform2 = new TransformActionConfiguration();
        transform1.setName("transform2");
        LoadActionConfiguration load = new LoadActionConfiguration();
        load.setName("load");

        ingressFlow.setTransformActions(List.of(transform1, transform2));
        ingressFlow.setLoadAction(load);

        List<FlowConfigError> errors = ingressFlowValidator.validate(ingressFlow);
        Mockito.verify(schemaComplianceValidator, Mockito.times(3)).validate(actionConfigCaptor.capture());

        List<ActionConfiguration> validatedActions = actionConfigCaptor.getAllValues();
        Assertions.assertThat(validatedActions).hasSize(3)
                .contains(transform1)
                .contains(transform2)
                .contains(load);

        Assertions.assertThat(errors).isEmpty();
    }

    @Test
    void validate_createErrors() {
        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName("ingress");
        LoadActionConfiguration load = new LoadActionConfiguration();
        load.setName("fail");
        ingressFlow.setLoadAction(load);

        FlowConfigError expected = expectedError();
        Mockito.when(schemaComplianceValidator.validate(Mockito.argThat((action) -> "fail".equals(action.getName()))))
                .thenReturn(List.of(expected));

        List<FlowConfigError> errors = ingressFlowValidator.validate(ingressFlow);

        Assertions.assertThat(errors).hasSize(1).contains(expected);
    }

    FlowConfigError expectedError() {
        FlowConfigError actionConfigError = new FlowConfigError();
        actionConfigError.setConfigName("brokenAction");
        actionConfigError.setErrorType(FlowErrorType.UNREGISTERED_ACTION);
        actionConfigError.setMessage("Action: brokenAction has not been registered with the system");
        return actionConfigError;
    }

}
