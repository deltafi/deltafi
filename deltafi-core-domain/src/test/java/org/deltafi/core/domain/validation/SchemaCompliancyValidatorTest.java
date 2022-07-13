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

import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.configuration.*;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowErrorType;
import org.deltafi.core.domain.services.ActionSchemaService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.core.domain.Util.egressSchema;

@ExtendWith(MockitoExtension.class)
class SchemaCompliancyValidatorTest {

    public static final String EGRESS_ACTION = "org.deltafi.core.action.RestPostEgressAction";
    public static final String ENRICH_ACTION = "org.deltafi.passthrough.action.RoteEnrichAction";
    public static final String FORMAT_ACTION = "org.deltafi.passthrough.action.RoteFormatAction";
    public static final String TRANSFORM_ACTION = "org.deltafi.passthrough.action.RoteTransformAction";
    public static final String PRODUCES_VALUE = "producesValue";
    public static final String DOMAIN_VALUE = "domainValue";
    public static final String ENRICHMENT_VALUE = "enrichmentValue";

    @InjectMocks
    SchemaCompliancyValidator schemaCompliancyValidator;

    @Mock
    ActionSchemaService actionSchemaService;

    @Spy
    DeltaFiProperties deltaFiProperties = new DeltaFiProperties();

    @BeforeEach
    public void setup() {
        deltaFiProperties.setActionInactivityThreshold(Duration.ofMinutes(5L));
    }

    @Test
    void runValidate() {
        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(egressSchemaOptional());
        assertThat(schemaCompliancyValidator.validate(egressConfig(getRequiredEgressParams()))).isEmpty();
    }

    @Test
    void runValidate_message() {
        Map<String, Object> params = getRequiredEgressParams();
        params.remove("url");
        params.put("url2", "https://egress");

        EgressActionConfiguration config = egressConfig(params);
        config.setName("egressName");

        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(egressSchemaOptional());
        List<FlowConfigError> errors = schemaCompliancyValidator.validate(config);

        assertThat(errors).hasSize(1);
        FlowConfigError error = errors.get(0);
        assertThat(error.getConfigName()).isEqualTo("egressName");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.INVALID_ACTION_PARAMETERS);
        assertThat(error.getMessage()).isEqualTo("$.url: is missing but it is required; $.url2: is not defined in the schema and the schema does not allow additional properties");
    }

    @Test
    void runValidate_missingType() {
        EgressActionConfiguration config = egressConfig(getRequiredEgressParams());
        config.setName("egressName");
        config.setType("   ");

        List<FlowConfigError> errors = schemaCompliancyValidator.validate(config);
        Mockito.verifyNoInteractions(actionSchemaService);
        FlowConfigError error = errors.get(0);
        assertThat(error.getConfigName()).isEqualTo("egressName");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.INVALID_CONFIG);
        assertThat(error.getMessage()).isEqualTo("The action configuration type cannot be null or empty");
    }

    @Test
    void validateAgainstSchema_wrongInstanceType() {
        EgressActionConfiguration config = egressConfig(getRequiredEgressParams());
        config.setType(FORMAT_ACTION);
        config.setName("egressAction");

        FormatActionSchema formatActionSchema = new FormatActionSchema();
        formatActionSchema.setLastHeard(OffsetDateTime.now());

        List<FlowConfigError> errors = schemaCompliancyValidator.validateAgainstSchema(formatActionSchema, config);
        FlowConfigError error = errors.get(0);
        assertThat(error.getConfigName()).isEqualTo("egressAction");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.INVALID_CONFIG);
        assertThat(error.getMessage()).isEqualTo("Action: org.deltafi.passthrough.action.RoteFormatAction is not registered as an EgressAction");
    }

    @Test
    void validateAgainstSchema_actionNotRegistered() {
        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(Optional.empty());
        List<FlowConfigError> errors = schemaCompliancyValidator.validate(egressConfig(null));
        FlowConfigError error = errors.get(0);
        assertThat(error.getConfigName()).isEqualTo("RestEgress");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.UNREGISTERED_ACTION);
        assertThat(error.getMessage()).isEqualTo("Action: org.deltafi.core.action.RestPostEgressAction has not been registered with the system");
    }

    @Test
    void validateAgainstSchema_inactiveAction() {
        EgressActionSchema schema = new EgressActionSchema();
        schema.setLastHeard(OffsetDateTime.parse("2021-12-31T00:00:00+00:00"));
        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(Optional.of(schema));
        List<FlowConfigError> errors = schemaCompliancyValidator.validate(egressConfig(null));
        assertThat(errors).hasSize(1);
        FlowConfigError error = errors.get(0);
        assertThat(error.getConfigName()).isEqualTo("RestEgress");
        assertThat(error.getErrorType()).isEqualTo(FlowErrorType.INACTIVE_ACTION);
        assertThat(error.getMessage()).isEqualTo("Action: org.deltafi.core.action.RestPostEgressAction has not been active since 2021-12-31T00:00Z");
    }

    @Test
    void validateAgainstSchema_goodTransform() {
        assertThat(schemaCompliancyValidator.validateAgainstSchema(transformSchema(), transformConfig())).isEmpty();
    }

    @Test
    void validateAgainstSchema_invalidTransform1() {
        List<FlowConfigError> errors = schemaCompliancyValidator.validateAgainstSchema(transformSchema(), invalidTransform1());

        assertThat(errors).hasSize(2)
                .contains(FlowConfigError.newBuilder().configName("MyTransform").errorType(FlowErrorType.INVALID_CONFIG).message("The action configuration consumes value must be: any").build())
                .contains(FlowConfigError.newBuilder().configName("MyTransform").errorType(FlowErrorType.INVALID_CONFIG).message("The action configuration produces value must be: producesValue").build());
    }

    @Test
    void validateAgainstSchema_invalidTransform2() {
        List<FlowConfigError> errors = schemaCompliancyValidator.validateAgainstSchema(transformSchema(), invalidTransform2());

        assertThat(errors).hasSize(1)
                .contains(FlowConfigError.newBuilder().configName("MyTransform").errorType(FlowErrorType.INVALID_CONFIG).message("The action configuration produces value must be: producesValue").build());
    }

    @Test
    void validateAgainstSchema_goodEnrich() {
        assertThat(schemaCompliancyValidator.validateAgainstSchema(enrichSchema(), enrichConfig())).isEmpty();
    }

    @Test
    void validateAgainstSchema_invalidEnrich() {
        List<FlowConfigError> errors = schemaCompliancyValidator.validateAgainstSchema(enrichSchema(), invalidEnrich());

        assertThat(errors).hasSize(2)
                .contains(FlowConfigError.newBuilder().configName("MyEnrich").errorType(FlowErrorType.INVALID_CONFIG).message("The action configuration requiresDomain value must be: [domainValue]").build())
                .contains(FlowConfigError.newBuilder().configName("MyEnrich").errorType(FlowErrorType.INVALID_CONFIG).message("The action configuration requiresEnrichment value must be: []").build());
    }

    @Test
    void validateAgainstSchema_goodFormat() {
        assertThat(schemaCompliancyValidator.validateAgainstSchema(formatSchema(), formatConfig())).isEmpty();
    }

    @Test
    void validateAgainstSchema_invalidFormat() {
        List<FlowConfigError> errors = schemaCompliancyValidator.validateAgainstSchema(formatSchema(), invalidFormat());
        assertThat(errors).hasSize(1).contains(FlowConfigError.newBuilder().configName("MyFormat").errorType(FlowErrorType.INVALID_CONFIG).message("The action configuration requiresDomains value must be: [domainValue]").build());
    }

    @Test
    void validateParameters_missingRequiredField() {
        Map<String, Object> params = getRequiredEgressParams();
        params.remove("url");

        List<FlowConfigError> errors = schemaCompliancyValidator.validateAgainstSchema(egressSchema(), egressConfig(params));
        assertThat(errors).hasSize(1)
                .contains(FlowConfigError.newBuilder().configName("RestEgress").errorType(FlowErrorType.INVALID_ACTION_PARAMETERS).message("$.url: is missing but it is required").build());
    }

    @Test
    void validateParameters_wrongType() {
        Map<String, Object> params = getRequiredEgressParams();
        params.put("url", true);

        List<FlowConfigError> errors = schemaCompliancyValidator.validateAgainstSchema(egressSchema(), egressConfig(params));
        assertThat(errors).hasSize(1)
                .contains(FlowConfigError.newBuilder().configName("RestEgress").errorType(FlowErrorType.INVALID_ACTION_PARAMETERS).message("$.url: boolean found, string expected").build());
    }

    @Test
    void validateParameters_unexpectedParam() {
        Map<String, Object> params = getRequiredEgressParams();
        params.put("unknownField", "not needed");

        List<FlowConfigError> errors = schemaCompliancyValidator.validateAgainstSchema(egressSchema(), egressConfig(params));
        assertThat(errors).hasSize(1)
                .contains(FlowConfigError.newBuilder().configName("RestEgress").errorType(FlowErrorType.INVALID_ACTION_PARAMETERS).message("$.unknownField: is not defined in the schema and the schema does not allow additional properties").build());
    }

    @Test
    void validateParameters_multipleErrors() {
        Map<String, Object> params = getRequiredEgressParams();
        params.remove("url");
        params.put("urlTypo", "http://egress");

        List<FlowConfigError> errors = schemaCompliancyValidator.validateAgainstSchema(egressSchema(), egressConfig(params));

        assertThat(errors).hasSize(1)
                .contains(FlowConfigError.newBuilder().configName("RestEgress").errorType(FlowErrorType.INVALID_ACTION_PARAMETERS).message("$.url: is missing but it is required; $.urlTypo: is not defined in the schema and the schema does not allow additional properties").build());
    }

    Optional<ActionSchema> egressSchemaOptional() {
        return Optional.of(egressSchema());
    }

    private EgressActionConfiguration egressConfig(Map<String, Object> params) {
        EgressActionConfiguration restEgressConfig = new EgressActionConfiguration();
        restEgressConfig.setName("RestEgress");
        restEgressConfig.setApiVersion("0.19.0");
        restEgressConfig.setType(EGRESS_ACTION);
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

    ActionSchema enrichSchema() {
        EnrichActionSchema schema = new EnrichActionSchema();
        schema.setId(ENRICH_ACTION);
        schema.setParamClass("paramClass");
        schema.setLastHeard(OffsetDateTime.now());
        schema.setRequiresDomains(Collections.singletonList(DOMAIN_VALUE));
        schema.setRequiresEnrichment(Collections.emptyList());
        return schema;
    }

    private EnrichActionConfiguration enrichConfig() {
        EnrichActionConfiguration config = new EnrichActionConfiguration();
        config.setName("MyEnrich");
        config.setApiVersion("0.19.0");
        config.setType(ENRICH_ACTION);
        config.setRequiresDomains(Collections.singletonList(DOMAIN_VALUE));
        return config;
    }

    private EnrichActionConfiguration invalidEnrich() {
        EnrichActionConfiguration config = new EnrichActionConfiguration();
        config.setName("MyEnrich");
        config.setApiVersion("0.19.0");
        config.setType(ENRICH_ACTION);
        // missing: requiresDomains
        // config has requiresEnrichment, but schema has an empty list.
        config.setRequiresEnrichment(Collections.singletonList(ENRICHMENT_VALUE));
        return config;
    }

    ActionSchema formatSchema() {
        FormatActionSchema schema = new FormatActionSchema();
        schema.setId(FORMAT_ACTION);
        schema.setParamClass("paramClass");
        schema.setLastHeard(OffsetDateTime.now());
        schema.setRequiresDomains(Collections.singletonList(DOMAIN_VALUE));
        schema.setRequiresEnrichment(Collections.singletonList(DeltaFiConstants.MATCHES_ANY));
        return schema;
    }

    private FormatActionConfiguration formatConfig() {
        FormatActionConfiguration config = new FormatActionConfiguration();
        config.setName("MyFormat");
        config.setApiVersion("0.19.0");
        config.setType(FORMAT_ACTION);
        config.setRequiresDomains(Collections.singletonList(DOMAIN_VALUE));
        config.setRequiresEnrichment(Collections.singletonList(ENRICHMENT_VALUE));
        return config;
    }

    private FormatActionConfiguration invalidFormat() {
        FormatActionConfiguration config = new FormatActionConfiguration();
        config.setName("MyFormat");
        config.setApiVersion("0.19.0");
        config.setType(FORMAT_ACTION);
        // wrong requiresDomains value:
        config.setRequiresDomains(Collections.singletonList("bogusDomain"));
        // no requiresEnrichment here is ok because schema is ANY
        return config;
    }

    ActionSchema transformSchema() {
        TransformActionSchema schema = new TransformActionSchema();
        schema.setId(TRANSFORM_ACTION);
        schema.setParamClass("paramClass");
        schema.setLastHeard(OffsetDateTime.now());
        schema.setConsumes(DeltaFiConstants.MATCHES_ANY);
        schema.setProduces(PRODUCES_VALUE);
        return schema;
    }

    private TransformActionConfiguration transformConfig() {
        TransformActionConfiguration config = new TransformActionConfiguration();
        config.setName("MyTransform");
        config.setApiVersion("0.19.0");
        config.setType(TRANSFORM_ACTION);
        config.setConsumes("data");
        config.setProduces(PRODUCES_VALUE);
        return config;
    }

    private TransformActionConfiguration invalidTransform1() {
        TransformActionConfiguration config = new TransformActionConfiguration();
        config.setName("MyTransform");
        config.setApiVersion("0.19.0");
        config.setType(TRANSFORM_ACTION);
        // consumes is null
        // produces is blank:
        config.setProduces("   ");
        return config;
    }

    private TransformActionConfiguration invalidTransform2() {
        TransformActionConfiguration config = new TransformActionConfiguration();
        config.setName("MyTransform");
        config.setApiVersion("0.19.0");
        config.setType(TRANSFORM_ACTION);
        config.setConsumes("data");
        // wrong produces:
        config.setProduces("bogusValue");
        return config;
    }

}
