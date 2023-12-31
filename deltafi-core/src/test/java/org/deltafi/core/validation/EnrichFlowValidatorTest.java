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

import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.EnrichActionConfiguration;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.types.EnrichFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EnrichFlowValidatorTest {

    @InjectMocks
    EnrichFlowValidator enrichFlowValidator;

    @Mock
    SchemaComplianceValidator schemaComplianceValidator;

    @Captor
    ArgumentCaptor<ActionConfiguration> actionConfigCaptor;

    @Test
    void validate_noErrors() {
        EnrichFlow enrichFlow = new EnrichFlow();
        enrichFlow.setName("enrichFlow");

        EnrichActionConfiguration enrich1 = new EnrichActionConfiguration("enrich1", "enrich1", null);
        EnrichActionConfiguration enrich2 = new EnrichActionConfiguration("enrich2", "enrich2", null);
        enrichFlow.setEnrichActions(List.of(enrich1, enrich2));

        List<FlowConfigError> errors = enrichFlowValidator.validate(enrichFlow);

        Mockito.verify(schemaComplianceValidator, Mockito.times(2)).validate(actionConfigCaptor.capture());

        List<ActionConfiguration> validatedActions = actionConfigCaptor.getAllValues();
        assertThat(validatedActions).hasSize(2)
                .contains(enrich1)
                .contains(enrich2);

        assertThat(errors).isEmpty();
    }

    @Test
    void validate_createErrors() {
        EnrichFlow enrichFlow = new EnrichFlow();
        enrichFlow.setName("enrichFlow");

        EnrichActionConfiguration enrich = new EnrichActionConfiguration("fail", "enrich", null);
        enrichFlow.setEnrichActions(List.of(enrich));

        FlowConfigError expected = expectedError();
        Mockito.when(schemaComplianceValidator.validate(Mockito.argThat((action) -> "fail".equals(action.getName()))))
                        .thenReturn(List.of(expected));

        List<FlowConfigError> errors = enrichFlowValidator.validate(enrichFlow);
        assertThat(errors).hasSize(1).contains(expected);
    }

    FlowConfigError expectedError() {
        FlowConfigError actionConfigError = new FlowConfigError();
        actionConfigError.setConfigName("brokenAction");
        actionConfigError.setErrorType(FlowErrorType.UNREGISTERED_ACTION);
        actionConfigError.setMessage("Action: brokenAction has not been registered with the system");
        return actionConfigError;
    }

}
