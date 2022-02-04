package org.deltafi.core.domain.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.services.ActionSchemaService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
public class ActionConfigurationValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);

    private final Duration actionInactivityThreshold;
    private final ActionSchemaService actionSchemaService;

    public ActionConfigurationValidator(ActionSchemaService actionSchemaService, DeltaFiProperties properties) {
        this.actionSchemaService = actionSchemaService;
        this.actionInactivityThreshold = properties.getActionInactivityThreshold();
    }

    public Optional<String> validateActionConfiguration(ActionConfiguration actionConfiguration) {
        List<String> errors = runValidateActionConfiguration(actionConfiguration);
        if (!errors.isEmpty()) {
            return Optional.of("Action Configuration: " + actionConfiguration + " has the following errors: \n" + String.join("; ", errors));
        }

        return Optional.empty();
    }

    public List<String> runValidateActionConfiguration(ActionConfiguration actionConfiguration) {
        List<String> errors = new ArrayList<>();

        if (missingName(actionConfiguration)) {
            errors.add("Required property name is not set");
        }

        errors.addAll(actionConfiguration.validate());

        if (missingType(actionConfiguration)) {
            errors.add("Required property type is not set");
        } else {
            errors.addAll(validateAgainstSchema(actionConfiguration));
        }

        return errors;
    }

    List<String> validateAgainstSchema(ActionConfiguration actionConfiguration) {
        return actionSchemaService.getByActionClass(actionConfiguration.getType())
            .map(schema -> this.validateAgainstSchema(schema, actionConfiguration))
            .orElseGet(() -> Collections.singletonList("Action type: " + actionConfiguration.getType() + " has not been registered with the system"));
    }

    List<String> validateAgainstSchema(ActionSchema actionSchema, ActionConfiguration actionConfiguration) {
        List<String> errors = new ArrayList<>();
        if (isInactive(actionSchema)) {
            errors.add("Action type: " + actionConfiguration.getType() + " has not been active since " + actionSchema.getLastHeard());
        }

        String paramErrors = validateParameters(actionConfiguration, actionSchema);
        if (!paramErrors.isBlank()) {
            errors.add(paramErrors);
        }

        return errors;
    }

    String validateParameters(ActionConfiguration actionConfig, ActionSchema actionSchema) {
        JsonNode schemaNode = OBJECT_MAPPER.convertValue(actionSchema.getSchema(), JsonNode.class);

        Map<String, Object> paramMap = Objects.nonNull(actionConfig.getParameters()) ? actionConfig.getParameters() : new HashMap<>();
        JsonNode params = OBJECT_MAPPER.convertValue(paramMap, JsonNode.class);

        final JsonSchema schema = factory.getSchema(schemaNode);

        schema.initializeValidators();

        Set<ValidationMessage> errors = schema.validate(params);
        String schemaErrors = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("; "));
        if (!schemaErrors.isBlank()) {
            return "Parameter Errors: " + schemaErrors;
        }

        return "";
    }

    boolean missingName(ActionConfiguration config) {
        return isBlank(config.getName());
    }

    boolean missingType(ActionConfiguration config) {
        return isBlank(config.getType());
    }

    boolean isInactive(ActionSchema schema) {
        return Objects.isNull(schema.getLastHeard()) || schema.getLastHeard().isBefore(OffsetDateTime.now().minus(actionInactivityThreshold));
    }

}
