package org.deltafi.core.domain.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.api.types.EgressActionSchema;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.configuration.EgressActionConfiguration;
import org.deltafi.core.domain.services.ActionSchemaService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ActionConfigurationValidatorTest {

    public static final String EGRESS_ACTION = "org.deltafi.core.action.RestPostEgressAction";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    @InjectMocks
    ActionConfigurationValidator actionConfigurationValidator;

    @Mock
    ActionSchemaService actionSchemaService;

    @Spy
    DeltaFiProperties deltaFiProperties = new DeltaFiProperties();

    @BeforeEach
    public void setup() {
        deltaFiProperties.setActionInactivityThreshold(Duration.ofMinutes(5L));
    }

    @Test
    void runValidateActionConfiguration() {
        Map<String, Object> params = getRequiredParams();

        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(actionSchemaOptional());
        Optional<String> errors = actionConfigurationValidator.validateActionConfiguration(egressConfig(params));

        assertThat(errors).isEmpty();
    }

    @Test
    void runValidateActionConfiguration_message() {
        Map<String, Object> params = getRequiredParams();
        params.remove("url");
        params.put("url2", "https://egress");

        EgressActionConfiguration config = egressConfig(params);
        config.setName(null);

        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(actionSchemaOptional());
        Optional<String> errors = actionConfigurationValidator.validateActionConfiguration(config);

        assertThat(errors).contains("Action Configuration: EgressActionConfiguration{name='null',created='null',modified='null',apiVersion='0.7.0',type='org.deltafi.core.action.RestPostEgressAction',parameters='{egressFlow=out, url2=https://egress, name=RestEgress}'} has the following errors: \n" +
                "Required property name is not set; Parameter Errors: $.url: is missing but it is required; $.url2: is not defined in the schema and the schema does not allow additional properties");
    }

    @Test
    void runValidateActionConfiguration_missingName() {
        EgressActionConfiguration config = egressConfig(getRequiredParams());
        config.setName(null);

        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(actionSchemaOptional());
        List<String> errors = actionConfigurationValidator.runValidateActionConfiguration(config);

        assertThat(errors).contains("Required property name is not set");
    }

    @Test
    void runValidateActionConfiguration_missingType() {
        EgressActionConfiguration config = egressConfig(getRequiredParams());
        config.setType("   ");

        List<String> errors = actionConfigurationValidator.runValidateActionConfiguration(config);
        Mockito.verifyNoInteractions(actionSchemaService);
        assertThat(errors).contains("Required property type is not set");
    }

    @Test
    void validateAgainstSchema_actionNotRegistered() {
        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(Optional.empty());
        List<String> errors = actionConfigurationValidator.validateAgainstSchema(egressConfig(null));
        assertThat(errors).contains("Action type: org.deltafi.core.action.RestPostEgressAction has not been registered with the system");
    }

    @Test
    void validateAgainstSchema_inactiveAction() {
        ActionSchema schema = actionSchemaMinus1Day();
        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(Optional.of(schema));
        List<String> errors = actionConfigurationValidator.validateAgainstSchema(egressConfig(getRequiredParams()));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Action type: org.deltafi.core.action.RestPostEgressAction has not been active since ");
    }

    @Test
    void validateParameters_missingRequiredField() {
        Map<String, Object> params = getRequiredParams();
        params.remove("url");

        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(actionSchemaOptional());
        List<String> errors = actionConfigurationValidator.runValidateActionConfiguration(egressConfig(params));

        assertThat(errors).contains("Parameter Errors: $.url: is missing but it is required");
    }

    @Test
    void validateParameters_wrongType() {
        Map<String, Object> params = getRequiredParams();
        params.put("url", true);

        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(actionSchemaOptional());
        List<String> errors = actionConfigurationValidator.runValidateActionConfiguration(egressConfig(params));
        assertThat(errors).contains("Parameter Errors: $.url: boolean found, string expected");
    }

    @Test
    void validateParameters_unexpectedParam() {
        Map<String, Object> params = getRequiredParams();
        params.put("unknownField", "not needed");

        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(actionSchemaOptional());
        List<String> errors = actionConfigurationValidator.runValidateActionConfiguration(egressConfig(params));
        assertThat(errors).contains("Parameter Errors: $.unknownField: is not defined in the schema and the schema does not allow additional properties");
    }

    @Test
    void validateParameters_multipleErrors() {
        Map<String, Object> params = getRequiredParams();
        params.remove("url");
        params.put("urlTypo", "http://egress");

        Mockito.when(actionSchemaService.getByActionClass(EGRESS_ACTION)).thenReturn(actionSchemaOptional());
        List<String> errors = actionConfigurationValidator.runValidateActionConfiguration(egressConfig(params));
        assertThat(errors).contains("Parameter Errors: $.url: is missing but it is required; $.urlTypo: is not defined in the schema and the schema does not allow additional properties");
    }

    Optional<ActionSchema> actionSchemaOptional() {
        return Optional.of(actionSchema());
    }

    ActionSchema actionSchema() {
        try {
            EgressActionSchema actionSchema = OBJECT_MAPPER.readValue(getClass().getClassLoader().getResource("config-test/rest-egress-schema.json"), new TypeReference<>() {
            });
            actionSchema.setLastHeard(OffsetDateTime.now());
            return actionSchema;
        } catch (IOException e) {
            Assertions.fail("Could not read sample action schema");
        }
        return null;
    }

    ActionSchema actionSchemaMinus1Day() {
        try {
            EgressActionSchema actionSchema = OBJECT_MAPPER.readValue(getClass().getClassLoader().getResource("config-test/rest-egress-schema.json"), new TypeReference<>() {
            });
            actionSchema.setLastHeard(OffsetDateTime.now().minusDays(1));
            return actionSchema;
        } catch (IOException e) {
            Assertions.fail("Could not read sample action schema");
        }
        return null;
    }

    private EgressActionConfiguration egressConfig(Map<String, Object> params) {
        EgressActionConfiguration restEgressConfig = new EgressActionConfiguration();
        restEgressConfig.setName("RestEgress");
        restEgressConfig.setApiVersion("0.7.0");
        restEgressConfig.setType(EGRESS_ACTION);
        restEgressConfig.setParameters(params);
        return restEgressConfig;
    }

    @NotNull
    private Map<String, Object> getRequiredParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("url", "https://egress");
        params.put("name", "RestEgress");
        params.put("egressFlow", "out");
        return params;
    }
}
