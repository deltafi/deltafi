package org.deltafi.core.domain.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.generated.types.ActionConfiguration;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.generated.types.FlowErrorType;
import org.deltafi.core.domain.generated.types.Variable;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

public class FlowPlanPropertyHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PLACEHOLDER_PREFIX = "${";
    private static final String PLACEHOLDER_SUFFIX = "}";

    private static final PropertyPlaceholderHelper PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(PLACEHOLDER_PREFIX, PLACEHOLDER_SUFFIX, null, false);

    private final VariablePlaceholderResolver flowPlanPlaceholderResolver;
    private final Set<FlowConfigError> errors;
    private final String actionNamePrefix;

    public FlowPlanPropertyHelper(List<Variable> variables, String actionNamePrefix) {
        this.flowPlanPlaceholderResolver = new VariablePlaceholderResolver(variables);
        this.actionNamePrefix = actionNamePrefix;
        this.errors = new HashSet<>();
    }

    public Set<FlowConfigError> getErrors() {
        return this.errors;
    }

    public <C extends ActionConfiguration> void replaceCommonActionPlaceholders(C actionConfiguration, ActionConfiguration actionTemplate) {
        String actionName = actionNamePrefix + "." + replaceValue(actionTemplate.getName(), actionTemplate.getName());
        actionConfiguration.setName(actionName);
        actionConfiguration.setType(actionTemplate.getType()); // type should never be templated
        actionConfiguration.setParameters(replaceParameterPlaceholders(actionTemplate.getParameters(), actionConfiguration.getName()));
    }

    public List<String> replaceListOfPlaceholders(List<String> values, String inActionNamed) {
        return nonNull(values) ? values.stream()
                .map(value -> this.replaceValue(value, inActionNamed))
                .collect(Collectors.toList()) : Collections.emptyList();
    }

    public List<KeyValue> replaceKeyValuePlaceholders(List<KeyValue> keyValues, String inActionNamed) {
        return nonNull(keyValues) ? keyValues.stream()
                .map(keyValue -> this.replaceKeyValuePlaceholders(keyValue, inActionNamed))
                .collect(Collectors.toList()) : Collections.emptyList();
    }

    public KeyValue replaceKeyValuePlaceholders(KeyValue keyValue, String inActionNamed) {
        if (Objects.isNull(keyValue)) {
            return null;
        }

        KeyValue resolvedKeyValue = new KeyValue();
        resolvedKeyValue.setKey(replaceValue(keyValue.getKey(), inActionNamed));
        resolvedKeyValue.setValue(replaceValue(keyValue.getValue(), inActionNamed));
        return resolvedKeyValue;
    }

    public Map<String, Object> replaceParameterPlaceholders(Map<String, Object> params, String inActionNamed) {
        try {
            String paramString = OBJECT_MAPPER.writeValueAsString(params);
            String resolvedParams = replaceValue(paramString, inActionNamed);
            return OBJECT_MAPPER.readValue(resolvedParams, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            FlowConfigError configError = new FlowConfigError();
            configError.setMessage("Failed to resolve parameters: " + e.getMessage());
            configError.setErrorType(FlowErrorType.UNRESOLVED_VARIABLE);
            configError.setConfigName(inActionNamed);
            errors.add(configError);
            return params;
        }
    }

    public String replaceValue(String value, String inActionNamed) {
        try {
            return nonNull(value) ? PLACEHOLDER_HELPER.replacePlaceholders(value, flowPlanPlaceholderResolver) : null;
        } catch (IllegalArgumentException e) {
            FlowConfigError configError = new FlowConfigError();
            configError.setConfigName(inActionNamed);
            configError.setErrorType(FlowErrorType.UNRESOLVED_VARIABLE);
            configError.setMessage(e.getMessage());
            errors.add(configError);
        }
        return value;
    }

    public Set<Variable> getAppliedVariables() {
        return flowPlanPlaceholderResolver.getAppliedVariables();
    }

}
