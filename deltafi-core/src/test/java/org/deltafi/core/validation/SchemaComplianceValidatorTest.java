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

import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.*;
import org.deltafi.core.util.Util;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.services.ActionDescriptorService;
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
    public static final String ENRICH_ACTION = "org.deltafi.passthrough.action.RoteEnrichAction";
    public static final String FORMAT_ACTION = "org.deltafi.passthrough.action.RoteFormatAction";
    public static final String TRANSFORM_ACTION = "org.deltafi.passthrough.action.RoteTransformAction";
    public static final String DOMAIN_VALUE = "domainValue";
    public static final String ENRICHMENT_VALUE = "enrichmentValue";

    @InjectMocks
    SchemaComplianceValidator schemaComplianceValidator;

    @Mock
    ActionDescriptorService actionDescriptorService;

    @Test
    void runValidate() {
        Mockito.when(actionDescriptorService.getByActionClass(EGRESS_ACTION)).thenReturn(egressActionDescriptorOptional());
        assertThat(schemaComplianceValidator.validate(egressConfig(getRequiredEgressParams()))).isEmpty();
    }

    @Test
    void runValidate_message() {
        Map<String, Object> params = getRequiredEgressParams();
        params.remove("url");
        params.put("url2", "https://egress");

        EgressActionConfiguration config = egressConfig("egressName", params);

        Mockito.when(actionDescriptorService.getByActionClass(EGRESS_ACTION)).thenReturn(egressActionDescriptorOptional());
        List<FlowConfigError> errors = schemaComplianceValidator.validate(config);

        assertThat(errors).hasSize(1);
        FlowConfigError error = errors.get(0);
        assertThat(error.getConfigName()).isEqualTo("egressName");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.INVALID_ACTION_PARAMETERS);
        assertThat(error.getMessage()).isEqualTo("$.url: is missing but it is required; $.url2: is not defined in the schema and the schema does not allow additional properties");
    }

    @Test
    void runValidate_missingType() {
        EgressActionConfiguration config = egressConfig("egressName", "   ", getRequiredEgressParams());

        List<FlowConfigError> errors = schemaComplianceValidator.validate(config);
        Mockito.verifyNoInteractions(actionDescriptorService);
        FlowConfigError error = errors.get(0);
        assertThat(error.getConfigName()).isEqualTo("egressName");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.INVALID_CONFIG);
        assertThat(error.getMessage()).isEqualTo("The action configuration type cannot be null or empty");
    }

    @Test
    void validateAgainstSchema_wrongInstanceType() {
        EgressActionConfiguration config = egressConfig("egressAction", FORMAT_ACTION, getRequiredEgressParams());

        List<FlowConfigError> errors = schemaComplianceValidator.validateAgainstSchema(
                ActionDescriptor.builder().type(ActionType.FORMAT).build(), config);

        FlowConfigError error = errors.get(0);
        assertThat(error.getConfigName()).isEqualTo("egressAction");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.INVALID_CONFIG);
        assertThat(error.getMessage()).isEqualTo("Action: org.deltafi.passthrough.action.RoteFormatAction is not registered as an action of type EGRESS");
    }

    @Test
    void validateAgainstSchema_actionNotRegistered() {
        Mockito.when(actionDescriptorService.getByActionClass(EGRESS_ACTION)).thenReturn(Optional.empty());

        List<FlowConfigError> errors = schemaComplianceValidator.validate(egressConfig(null));
        FlowConfigError error = errors.get(0);
        assertThat(error.getConfigName()).isEqualTo("RestEgress");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.UNREGISTERED_ACTION);
        assertThat(error.getMessage()).isEqualTo("Action: org.deltafi.core.action.RestPostEgressAction has not been registered with the system");
    }

    @Test
    void validateAgainstSchema_goodTransform() {
        assertThat(schemaComplianceValidator.validateAgainstSchema(transformActionDescriptor(), transformConfig())).isEmpty();
    }

    @Test
    void validateAgainstSchema_goodEnrich() {
        assertThat(schemaComplianceValidator.validateAgainstSchema(enrichActionDescriptor(), enrichConfig())).isEmpty();
    }

    @Test
    void validateAgainstSchema_invalidEnrich() {
        List<FlowConfigError> errors = schemaComplianceValidator.validateAgainstSchema(enrichActionDescriptor(), invalidEnrich());

        assertThat(errors).hasSize(2)
                .contains(FlowConfigError.newBuilder().configName("MyEnrich").errorType(FlowErrorType.INVALID_CONFIG).message("The action configuration requiresDomains value must be: [domainValue]").build())
                .contains(FlowConfigError.newBuilder().configName("MyEnrich").errorType(FlowErrorType.INVALID_CONFIG).message("The action configuration requiresEnrichments value must be: []").build());
    }

    @Test
    void validateAgainstSchema_goodFormat() {
        assertThat(schemaComplianceValidator.validateAgainstSchema(formatActionDescriptor(), formatConfig())).isEmpty();
    }

    @Test
    void validateAgainstSchema_invalidFormat() {
        List<FlowConfigError> errors = schemaComplianceValidator.validateAgainstSchema(formatActionDescriptor(), invalidFormat());
        assertThat(errors).hasSize(1).contains(FlowConfigError.newBuilder().configName("MyFormat").errorType(FlowErrorType.INVALID_CONFIG).message("The action configuration requiresDomains value must be: [domainValue]").build());
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

    private EgressActionConfiguration egressConfig(Map<String, Object> params) {
        return egressConfig("RestEgress", EGRESS_ACTION, params);
    }

    private EgressActionConfiguration egressConfig(String name, Map<String, Object> params) {
        return egressConfig(name, EGRESS_ACTION, params);
    }

    private EgressActionConfiguration egressConfig(String name, String type, Map<String, Object> params) {
        EgressActionConfiguration restEgressConfig = new EgressActionConfiguration(name, type);
        restEgressConfig.setApiVersion("0.19.0");
        restEgressConfig.setParameters(params);
        return restEgressConfig;
    }

    @NotNull
    private Map<String, Object> getRequiredEgressParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("url", "https://egress");
        params.put("name", "RestEgress");
        params.put("egressFlow", "out");
        return params;
    }

    ActionDescriptor enrichActionDescriptor() {
        return ActionDescriptor.builder()
                .name(ENRICH_ACTION)
                .type(ActionType.ENRICH)
                .requiresDomains(Collections.singletonList(DOMAIN_VALUE))
                .requiresEnrichments(Collections.emptyList())
                .build();
    }

    private EnrichActionConfiguration enrichConfig() {
        EnrichActionConfiguration config = new EnrichActionConfiguration("MyEnrich", ENRICH_ACTION, Collections.singletonList(DOMAIN_VALUE));
        config.setApiVersion("0.19.0");
        return config;
    }

    private EnrichActionConfiguration invalidEnrich() {
        // missing: requiresDomains
        EnrichActionConfiguration config = new EnrichActionConfiguration("MyEnrich", ENRICH_ACTION, null);
        config.setApiVersion("0.19.0");
        // config has requiresEnrichments, but schema has an empty list.
        config.setRequiresEnrichments(Collections.singletonList(ENRICHMENT_VALUE));
        return config;
    }

    ActionDescriptor formatActionDescriptor() {
        return ActionDescriptor.builder()
                .name(FORMAT_ACTION)
                .type(ActionType.FORMAT)
                .requiresDomains(Collections.singletonList(DOMAIN_VALUE))
                .requiresEnrichments(Collections.singletonList(DeltaFiConstants.MATCHES_ANY))
                .build();
    }

    private FormatActionConfiguration formatConfig() {
        FormatActionConfiguration config = new FormatActionConfiguration("MyFormat", FORMAT_ACTION, Collections.singletonList(DOMAIN_VALUE));
        config.setApiVersion("0.19.0");
        config.setRequiresEnrichments(Collections.singletonList(ENRICHMENT_VALUE));
        return config;
    }

    private FormatActionConfiguration invalidFormat() {
        // wrong requiresDomains value:
        FormatActionConfiguration config = new FormatActionConfiguration("MyFormat", FORMAT_ACTION, Collections.singletonList("bogusDomain"));
        config.setApiVersion("0.19.0");
        // no requiresEnrichments here is ok because schema is ANY
        return config;
    }

    ActionDescriptor transformActionDescriptor() {
        return ActionDescriptor.builder()
                .name(TRANSFORM_ACTION)
                .type(ActionType.TRANSFORM)
                .build();
    }

    private TransformActionConfiguration transformConfig() {
        return new TransformActionConfiguration("MyTransform", TRANSFORM_ACTION);
    }
}
