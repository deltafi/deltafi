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
import org.deltafi.core.domain.configuration.LoadActionConfiguration;
import org.deltafi.core.domain.configuration.TransformActionConfiguration;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowErrorType;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.FlowStatus;
import org.deltafi.core.domain.types.IngressFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class IngressFlowValidatorTest {

    @InjectMocks
    IngressFlowValidator ingressFlowValidator;

    @Mock
    SchemaCompliancyValidator schemaCompliancyValidator;

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

        ingressFlow.setType("json");
        ingressFlow.setTransformActions(List.of(transform1, transform2));
        ingressFlow.setLoadAction(load);

        ingressFlowValidator.validate(ingressFlow);
        Mockito.verify(schemaCompliancyValidator, Mockito.times(3)).validate(actionConfigCaptor.capture());

        List<ActionConfiguration> validatedActions = actionConfigCaptor.getAllValues();
        Assertions.assertThat(validatedActions).hasSize(3)
                .contains(transform1)
                .contains(transform2)
                .contains(load);

        Assertions.assertThat(ingressFlow.getFlowStatus().getState()).isEqualTo(FlowState.STOPPED);
        Assertions.assertThat(ingressFlow.getFlowStatus().getErrors()).isEmpty();
    }

    @Test
    void blankNameAndTypeError() {
        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName("  ");
        ingressFlow.setType("  ");

        LoadActionConfiguration load = new LoadActionConfiguration();
        load.setName("load");
        ingressFlow.setLoadAction(load);

        ingressFlowValidator.validate(ingressFlow);

        Assertions.assertThat(ingressFlow.getFlowStatus().getState()).isEqualTo(FlowState.INVALID);
        Assertions.assertThat(ingressFlow.getFlowStatus().getErrors())
                .hasSize(2)
                .contains(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG).configName("  ").message("The ingress flow type cannot be blank").build())
                .contains(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG).configName("  ").message("The flow name cannot be blank").build());
    }

    @Test
    void duplicateActionNameErrors() {
        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName("flow");
        ingressFlow.setType("json");

        LoadActionConfiguration load = new LoadActionConfiguration();
        load.setName("action");
        load.setType("org.deltafi.load.Action");
        ingressFlow.setLoadAction(load);

        TransformActionConfiguration transform1 = new TransformActionConfiguration();
        transform1.setName("action");
        transform1.setType("org.deltafi.transform.Action1");
        TransformActionConfiguration transform2 = new TransformActionConfiguration();
        transform2.setName("transform");
        transform2.setType("org.deltafi.transform.Action2");
        TransformActionConfiguration transform3 = new TransformActionConfiguration();
        transform3.setName("transform");
        transform3.setType("org.deltafi.transform.Action3");

        ingressFlow.setTransformActions(List.of(transform1, transform2, transform3));

        ingressFlowValidator.validate(ingressFlow);

        Assertions.assertThat(ingressFlow.getFlowStatus().getState()).isEqualTo(FlowState.INVALID);
        Assertions.assertThat(ingressFlow.getFlowStatus().getErrors())
                .hasSize(2)
                .contains(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG).configName("action")
                        .message("The action name: action is duplicated for the following action types: org.deltafi.load.Action, org.deltafi.transform.Action1").build())
                .contains(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG).configName("transform").message("The action name: transform is duplicated for the following action types: org.deltafi.transform.Action2, org.deltafi.transform.Action3").build());
    }

    @Test
    void validate_createErrors() {
        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName("ingress");
        LoadActionConfiguration load = new LoadActionConfiguration();
        load.setName("fail");
        ingressFlow.setType("json");
        ingressFlow.setLoadAction(load);

        FlowConfigError expected = expectedError();
        Mockito.when(schemaCompliancyValidator.validate(Mockito.argThat((action) -> "fail".equals(action.getName()))))
                .thenReturn(List.of(expected));

        ingressFlowValidator.validate(ingressFlow);

        FlowStatus status = ingressFlow.getFlowStatus();
        Assertions.assertThat(status.getState()).isEqualTo(FlowState.INVALID);
        Assertions.assertThat(status.getErrors()).hasSize(1).contains(expected);
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
    void validateLoadActionIsReachable_valid() {
        LoadActionConfiguration config = new LoadActionConfiguration();
        config.setConsumes("flowType");

        List<String> errors = ingressFlowValidator.validateLoadActionIsReachable(config, emptySet(), "flowType");
        assertThat(errors).isEmpty();
    }

    @Test
    void validateLoadActionIsReachable_flowTypeNotConsumed() {
        LoadActionConfiguration config = new LoadActionConfiguration();
        config.setConsumes("other");
        config.setName("loader");

        List<String> errors = ingressFlowValidator.validateLoadActionIsReachable(config, emptySet(), "flowType");
        assertThat(errors).hasSize(2)
                .contains("Load Action named: loader consumes: other which isn't produced in this flow")
                .contains("None of the configured Load Actions in this flow consume the ingress flow type: flowType");
    }

    FlowConfigError expectedError() {
        FlowConfigError actionConfigError = new FlowConfigError();
        actionConfigError.setConfigName("brokenAction");
        actionConfigError.setErrorType(FlowErrorType.UNREGISTERED_ACTION);
        actionConfigError.setMessage("Action: brokenAction has not been registered with the system");
        return actionConfigError;
    }

}
