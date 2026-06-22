/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

import tools.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
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

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2019_09,
            builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder()
                    .pathType(PathType.JSON_PATH)
                    .typeLoose(true)
                    .build()));

    private SchemaComplianceUtil() {}

    public static Optional<FlowConfigError> validateParameters(ActionConfiguration actionConfig, ActionDescriptor actionDescriptor) {
        Map<String, Object> schema = actionDescriptor.getSchema();
        try {
            actionConfig.setTemplated(ParameterUtil.hasTemplates(actionConfig.getParameters()));
        } catch (ParameterTemplateException e) {
            actionConfig.setTemplated(true);
            actionConfig.setParameterSchema(schema);
            return Optional.of(FlowConfigError.newBuilder().configName(actionConfig.getName()).errorType(FlowErrorType.INVALID_ACTION_PARAMETERS).message(e.getMessage()).build());
        }

        // if this configuration includes templates set the schema for validating after the parameters are fully resolved and return
        if (actionConfig.isTemplated()) {
            actionConfig.setParameterSchema(schema);
            return Optional.empty();
        }

        return validateParameters(actionConfig.getName(), schema, actionConfig.getInternalParameters());
    }

    public static Optional<FlowConfigError> validateParameters(String actionName, Map<String, Object> schemaMap, Map<String, Object> paramMap) {
        paramMap = Objects.requireNonNullElseGet(paramMap, HashMap::new);
        JsonNode schemaNode = ParameterUtil.toJsonNode(schemaMap);
        final Schema schema = REGISTRY.getSchema(schemaNode);
        JsonNode params = ParameterUtil.toJsonNode(paramMap);

        List<Error> errors = schema.validate(params);
        String schemaErrors = errors.stream().map(Error::toString).collect(Collectors.joining("; "));
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
