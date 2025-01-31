/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import com.networknt.schema.*;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.util.ParameterTemplateException;
import org.deltafi.common.util.ParameterUtil;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SchemaComplianceUtil {

    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);
    private static final SchemaValidatorsConfig validatorsConfig = SchemaValidatorsConfig.builder()
            .pathType(PathType.JSON_PATH)
            .typeLoose(true)
            .build();

    private SchemaComplianceUtil() {}

    public static Optional<FlowConfigError> validateParameters(ActionConfiguration actionConfig, ActionDescriptor actionDescriptor) {
        JsonNode schemaNode = ParameterUtil.toJsonNode(actionDescriptor.getSchema());
        try {
            actionConfig.setTemplated(ParameterUtil.hasTemplates(actionConfig.getParameters()));
        } catch (ParameterTemplateException e) {
            actionConfig.setTemplated(true);
            actionConfig.setParameterSchema(schemaNode);
            return Optional.of(FlowConfigError.newBuilder().configName(actionConfig.getName()).errorType(FlowErrorType.INVALID_ACTION_PARAMETERS).message(e.getMessage()).build());
        }

        // if this configuration includes templates set the schema for validating after the parameters are fully resolved and return
        if (actionConfig.isTemplated()) {
            actionConfig.setParameterSchema(schemaNode);
            return Optional.empty();
        }

        return validateParameters(actionConfig.getName(), schemaNode, actionConfig.getInternalParameters());
    }

    public static Optional<FlowConfigError> validateParameters(String actionName, JsonNode schemaNode, Map<String, Object> paramMap ) {
        paramMap = Objects.requireNonNullElseGet(paramMap, HashMap::new);
        final JsonSchema schema = FACTORY.getSchema(schemaNode, validatorsConfig);
        JsonNode params = ParameterUtil.toJsonNode(paramMap);

        schema.initializeValidators();

        Set<ValidationMessage> errors = schema.validate(params);
        String schemaErrors = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("; "));
        if (!schemaErrors.isBlank()) {
            return Optional.of(parameterErrors(actionName, schemaErrors));
        }

        return Optional.empty();
    }

    private static FlowConfigError parameterErrors(String actionName, String parameterErrors) {
        FlowConfigError configError = new FlowConfigError();
        configError.setConfigName(actionName);
        configError.setErrorType(FlowErrorType.INVALID_ACTION_PARAMETERS);
        configError.setMessage(parameterErrors);
        return configError;
    }
}
