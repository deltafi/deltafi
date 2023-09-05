/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.LoadActionConfiguration;
import org.deltafi.common.types.TransformActionConfiguration;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.types.NormalizeFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class NormalizeFlowValidatorTest {

    @InjectMocks
    NormalizeFlowValidator normalizeFlowValidator;

    @Mock
    SchemaComplianceValidator schemaComplianceValidator;

    @Captor
    ArgumentCaptor<ActionConfiguration> actionConfigCaptor;

    @Test
    void validate() {
        NormalizeFlow normalizeFlow = new NormalizeFlow();
        normalizeFlow.setName("ingress");
        TransformActionConfiguration transform1 = new TransformActionConfiguration("transform1", null);
        TransformActionConfiguration transform2 = new TransformActionConfiguration("transform2", null);
        LoadActionConfiguration load = new LoadActionConfiguration("load", null);

        normalizeFlow.setTransformActions(List.of(transform1, transform2));
        normalizeFlow.setLoadAction(load);

        List<FlowConfigError> errors = normalizeFlowValidator.validate(normalizeFlow);
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
        NormalizeFlow normalizeFlow = new NormalizeFlow();
        normalizeFlow.setName("ingress");
        LoadActionConfiguration load = new LoadActionConfiguration("fail", null);
        normalizeFlow.setLoadAction(load);

        FlowConfigError expected = expectedError();
        Mockito.when(schemaComplianceValidator.validate(Mockito.argThat((action) -> "fail".equals(action.getName()))))
                .thenReturn(List.of(expected));

        List<FlowConfigError> errors = normalizeFlowValidator.validate(normalizeFlow);

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
