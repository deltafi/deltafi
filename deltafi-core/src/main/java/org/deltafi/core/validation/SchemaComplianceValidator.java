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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.plugin.PluginRegistryService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SchemaComplianceValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);

    private final PluginRegistryService pluginService;
    private final SchemaValidatorsConfig validatorsConfig;

    public SchemaComplianceValidator(@Lazy PluginRegistryService pluginService) {
        this.pluginService = pluginService;
        validatorsConfig = new SchemaValidatorsConfig();
        validatorsConfig.setTypeLoose(true);
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

    private List<FlowConfigError> validateAgainstSchema(ActionConfiguration actionConfiguration) {
        return pluginService.getByActionClass(actionConfiguration.getType())
                .map(actionDescriptor -> validateAgainstSchema(actionDescriptor, actionConfiguration))
                .orElseGet(() -> Collections.singletonList(notRegisteredError(actionConfiguration)));
    }

    public List<FlowConfigError> validateAgainstSchema(ActionDescriptor actionDescriptor, ActionConfiguration actionConfiguration) {
        List<FlowConfigError> errors = new ArrayList<>();

        if (!actionDescriptor.isSupportsJoin() && actionConfiguration.getJoin() != null) {
            errors.add(FlowConfigError.newBuilder()
                    .configName(actionConfiguration.getName())
                    .errorType(FlowErrorType.INVALID_CONFIG)
                    .message("Attempted to configure join in an action that does not support join")
                    .build());
        }

        validateParameters(actionConfiguration, actionDescriptor).ifPresent(errors::add);

        actionConfiguration.validate(actionDescriptor).stream()
                .map(message -> actionConfigError(actionConfiguration, message))
                .forEach(errors::add);

        return errors;
    }

    private Optional<FlowConfigError> validateParameters(ActionConfiguration actionConfig, ActionDescriptor actionDescriptor) {
        JsonNode schemaNode = OBJECT_MAPPER.convertValue(actionDescriptor.getSchema(), JsonNode.class);

        Map<String, Object> paramMap = Objects.nonNull(actionConfig.getInternalParameters()) ? actionConfig.getInternalParameters() : new HashMap<>();
        JsonNode params = OBJECT_MAPPER.convertValue(paramMap, JsonNode.class);

        final JsonSchema schema = FACTORY.getSchema(schemaNode, validatorsConfig);

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
}
