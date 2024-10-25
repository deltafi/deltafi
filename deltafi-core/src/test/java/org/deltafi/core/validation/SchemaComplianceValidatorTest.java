/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.common.types.*;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.util.Util;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SchemaComplianceValidatorTest {

    public static final String EGRESS_ACTION = "org.deltafi.core.action.RestPostEgressAction";
    public static final String TRANSFORM_ACTION = "org.deltafi.passthrough.action.RoteTransformAction";

    @InjectMocks
    SchemaComplianceValidator schemaComplianceValidator;

    @Mock
    PluginService pluginService;

    @Test
    void runValidate() {
        Mockito.when(pluginService.getByActionClass(EGRESS_ACTION)).thenReturn(egressActionDescriptorOptional());
        assertThat(schemaComplianceValidator.validate(egressConfig(getRequiredEgressParams()))).isEmpty();
    }

    @Test
    void runValidate_message() {
        Map<String, Object> params = getRequiredEgressParams();
        params.remove("url");
        params.put("url2", "https://egress");

        ActionConfiguration config = egressConfig("egressName", params);

        Mockito.when(pluginService.getByActionClass(EGRESS_ACTION)).thenReturn(egressActionDescriptorOptional());
        List<FlowConfigError> errors = schemaComplianceValidator.validate(config);

        assertThat(errors).hasSize(1);
        FlowConfigError error = errors.getFirst();
        assertThat(error.getConfigName()).isEqualTo("egressName");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.INVALID_ACTION_PARAMETERS);
        assertThat(error.getMessage()).isEqualTo("$.url: is missing but it is required; $.url2: is not defined in the schema and the schema does not allow additional properties");
    }

    @Test
    void runValidate_missingType() {
        ActionConfiguration config = egressConfig("egressName", "   ", getRequiredEgressParams());

        List<FlowConfigError> errors = schemaComplianceValidator.validate(config);
        Mockito.verifyNoInteractions(pluginService);
        FlowConfigError error = errors.getFirst();
        assertThat(error.getConfigName()).isEqualTo("egressName");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.INVALID_CONFIG);
        assertThat(error.getMessage()).isEqualTo("The action configuration type cannot be null or empty");
    }

    @Test
    void validateAgainstSchema_wrongInstanceType() {
        ActionConfiguration config = egressConfig("egressAction", getRequiredEgressParams());

        List<FlowConfigError> errors = schemaComplianceValidator.validateAgainstSchema(
                ActionDescriptor.builder().type(ActionType.INGRESS).build(), config);

        FlowConfigError error = errors.getFirst();
        assertThat(error.getConfigName()).isEqualTo("egressAction");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.INVALID_CONFIG);
        assertThat(error.getMessage()).isEqualTo("Action: org.deltafi.core.action.RestPostEgressAction is not registered as an action of type EGRESS");
    }

    @Test
    void validateAgainstSchema_actionNotRegistered() {
        Mockito.when(pluginService.getByActionClass(EGRESS_ACTION)).thenReturn(Optional.empty());

        List<FlowConfigError> errors = schemaComplianceValidator.validate(egressConfig(null));
        FlowConfigError error = errors.getFirst();
        assertThat(error.getConfigName()).isEqualTo("RestEgress");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.UNREGISTERED_ACTION);
        assertThat(error.getMessage()).isEqualTo("Action: org.deltafi.core.action.RestPostEgressAction has not been registered with the system");
    }

    @Test
    void validateAgainstSchema_goodTransform() {
        assertThat(schemaComplianceValidator.validateAgainstSchema(transformActionDescriptor(), transformConfig())).isEmpty();
    }

    @Test
    void validateParameters_missingRequiredField() {
        Map<String, Object> params = getRequiredEgressParams();
        params.remove("url");

        List<FlowConfigError> errors = schemaComplianceValidator.validateAgainstSchema(Util.egressActionDescriptor(), egressConfig(params));
        assertThat(errors).hasSize(1)
                .contains(FlowConfigError.newBuilder().configName("RestEgress").errorType(FlowErrorType.INVALID_ACTION_PARAMETERS).message("$.url: is missing but it is required").build());
    }

    @Test
    void validateParameters_wrongType() {
        Map<String, Object> params = getRequiredEgressParams();
        params.put("url", true);

        List<FlowConfigError> errors = schemaComplianceValidator.validateAgainstSchema(Util.egressActionDescriptor(), egressConfig(params));
        assertThat(errors).hasSize(1)
                .contains(FlowConfigError.newBuilder().configName("RestEgress").errorType(FlowErrorType.INVALID_ACTION_PARAMETERS).message("$.url: boolean found, string expected").build());
    }

    @Test
    void validateParameters_unexpectedParam() {
        Map<String, Object> params = getRequiredEgressParams();
        params.put("unknownField", "not needed");

        List<FlowConfigError> errors = schemaComplianceValidator.validateAgainstSchema(Util.egressActionDescriptor(), egressConfig(params));
        assertThat(errors).hasSize(1)
                .contains(FlowConfigError.newBuilder().configName("RestEgress").errorType(FlowErrorType.INVALID_ACTION_PARAMETERS).message("$.unknownField: is not defined in the schema and the schema does not allow additional properties").build());
    }

    @Test
    void validateParameters_multipleErrors() {
        Map<String, Object> params = getRequiredEgressParams();
        params.remove("url");
        params.put("urlTypo", "http://egress");

        List<FlowConfigError> errors = schemaComplianceValidator.validateAgainstSchema(Util.egressActionDescriptor(), egressConfig(params));

        assertThat(errors).hasSize(1)
                .contains(FlowConfigError.newBuilder().configName("RestEgress").errorType(FlowErrorType.INVALID_ACTION_PARAMETERS).message("$.url: is missing but it is required; $.urlTypo: is not defined in the schema and the schema does not allow additional properties").build());
    }

    Optional<ActionDescriptor> egressActionDescriptorOptional() {
        return Optional.of(Util.egressActionDescriptor());
    }

    private ActionConfiguration egressConfig(Map<String, Object> params) {
        return egressConfig("RestEgress", EGRESS_ACTION, params);
    }

    private ActionConfiguration egressConfig(String name, Map<String, Object> params) {
        return egressConfig(name, EGRESS_ACTION, params);
    }

    private ActionConfiguration egressConfig(String name, String type, Map<String, Object> params) {
        ActionConfiguration restEgressConfig = new ActionConfiguration(name, ActionType.EGRESS, type);
        restEgressConfig.setApiVersion("0.19.0");
        restEgressConfig.setInternalParameters(params);
        return restEgressConfig;
    }

    @NotNull
    private Map<String, Object> getRequiredEgressParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("url", "https://egress");
        params.put("name", "RestEgress");
        params.put("dataSink", "out");
        return params;
    }

    ActionDescriptor transformActionDescriptor() {
        return ActionDescriptor.builder()
                .name(TRANSFORM_ACTION)
                .type(ActionType.TRANSFORM)
                .build();
    }

    private ActionConfiguration transformConfig() {
        return new ActionConfiguration("MyTransform", ActionType.TRANSFORM, TRANSFORM_ACTION);
    }
}
