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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowErrorType;
import org.deltafi.core.domain.services.ActionSchemaService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchemaCompliancyValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);

    private final Duration actionInactivityThreshold;
    private final ActionSchemaService actionSchemaService;

    public SchemaCompliancyValidator(ActionSchemaService actionSchemaService, DeltaFiProperties properties) {
        this.actionSchemaService = actionSchemaService;
        this.actionInactivityThreshold = properties.getActionInactivityThreshold();
    }

    public List<FlowConfigError> validate(ActionConfiguration actionConfiguration) {
        List<FlowConfigError> errors = new ArrayList<>();

        if (StringUtils.isBlank(actionConfiguration.getName())) {
            errors.add(actionConfigError(actionConfiguration, "The action configuration name cannot be null or empty"));
        }

        if (StringUtils.isBlank(actionConfiguration.getType())) {
            errors.add(actionConfigError(actionConfiguration, "The action configuration type cannot be null or empty"));
            return errors;
        }

        errors.addAll(validateAgainstSchema(actionConfiguration));
        return errors;
    }

    List<FlowConfigError> validateAgainstSchema(ActionConfiguration actionConfiguration) {
        return actionSchemaService.getByActionClass(actionConfiguration.getType())
                .map(schema -> this.validateAgainstSchema(schema, actionConfiguration))
                .orElseGet(() -> Collections.singletonList(notRegisteredError(actionConfiguration)));
    }

    List<FlowConfigError> validateAgainstSchema(ActionSchema actionSchema, ActionConfiguration actionConfiguration) {
        List<FlowConfigError> errors = new ArrayList<>();
        if (isInactive(actionSchema)) {
            errors.add(inactiveActionError(actionConfiguration, actionSchema.getLastHeard()));
        }

        validateParameters(actionConfiguration, actionSchema).ifPresent(errors::add);

        actionConfiguration.validate(actionSchema).stream()
                .map(message -> actionConfigError(actionConfiguration, message))
                .forEach(errors::add);

        return errors;
    }

    Optional<FlowConfigError> validateParameters(ActionConfiguration actionConfig, ActionSchema actionSchema) {
        JsonNode schemaNode = OBJECT_MAPPER.convertValue(actionSchema.getSchema(), JsonNode.class);

        Map<String, Object> paramMap = Objects.nonNull(actionConfig.getParameters()) ? actionConfig.getParameters() : new HashMap<>();
        JsonNode params = OBJECT_MAPPER.convertValue(paramMap, JsonNode.class);

        final JsonSchema schema = factory.getSchema(schemaNode);

        schema.initializeValidators();

        Set<ValidationMessage> errors = schema.validate(params);
        String schemaErrors = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("; "));
        if (!schemaErrors.isBlank()) {
            return Optional.of(parameterErrors(actionConfig, schemaErrors));
        }

        return Optional.empty();
    }

    FlowConfigError notRegisteredError(ActionConfiguration actionConfiguration) {
        FlowConfigError actionConfigError = new FlowConfigError();
        actionConfigError.setConfigName(actionConfiguration.getName());
        actionConfigError.setErrorType(FlowErrorType.UNREGISTERED_ACTION);
        actionConfigError.setMessage("Action: " + actionConfiguration.getType() + " has not been registered with the system");
        return actionConfigError;
    }

    FlowConfigError inactiveActionError(ActionConfiguration actionConfiguration, OffsetDateTime lastHeard) {
        FlowConfigError actionConfigError = new FlowConfigError();
        actionConfigError.setConfigName(actionConfiguration.getName());
        actionConfigError.setErrorType(FlowErrorType.INACTIVE_ACTION);
        actionConfigError.setMessage("Action: " + actionConfiguration.getType() + " has not been active since " + lastHeard);
        return actionConfigError;
    }

    FlowConfigError parameterErrors(ActionConfiguration actionConfiguration, String parameterErrors) {
        FlowConfigError configError = new FlowConfigError();
        configError.setConfigName(actionConfiguration.getName());
        configError.setErrorType(FlowErrorType.INVALID_ACTION_PARAMETERS);
        configError.setMessage(parameterErrors);
        return configError;
    }

    FlowConfigError actionConfigError(ActionConfiguration actionConfiguration, String message) {
        FlowConfigError configError = new FlowConfigError();
        configError.setConfigName(actionConfiguration.getName());
        configError.setErrorType(FlowErrorType.INVALID_CONFIG);
        configError.setMessage(message);
        return configError;
    }

    boolean isInactive(ActionSchema schema) {
        return Objects.isNull(schema.getLastHeard()) || schema.getLastHeard().isBefore(OffsetDateTime.now().minus(actionInactivityThreshold));
    }

}
