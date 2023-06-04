/**
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
package org.deltafi.core.converters;

import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.common.types.Variable;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.*;

import static java.util.Objects.nonNull;

public class FlowPlanPropertyHelper {

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

    public String getReplacedName(ActionConfiguration actionTemplate) {
        return actionNamePrefix + "." + replaceValue(actionTemplate.getName(), actionTemplate.getName());
    }

    public <C extends ActionConfiguration> void replaceCommonActionPlaceholders(C actionConfiguration, ActionConfiguration actionTemplate) {
        actionConfiguration.setParameters(replaceMapPlaceholders(actionTemplate.getParameters(), actionConfiguration.getName()));
    }

    public List<String> replaceListOfPlaceholders(List<String> values, String inActionNamed) {
        return nonNull(values) ? values.stream()
                .map(value -> this.replaceValue(value, inActionNamed))
                .toList() : Collections.emptyList();
    }

    public List<KeyValue> replaceKeyValuePlaceholders(List<KeyValue> keyValues, String inActionNamed) {
        return nonNull(keyValues) ? keyValues.stream()
                .map(keyValue -> this.replaceKeyValuePlaceholders(keyValue, inActionNamed))
                .toList() : Collections.emptyList();
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

    public Map<String, Object> replaceMapPlaceholders(Map<String, Object> params, String inActionNamed) {
        if (null == params || params.isEmpty()) {
            return params;
        }

        try {
            Map<String, Object> resolvedMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (null == entry.getKey() || null == entry.getValue()) {
                    continue;
                }

                String resolvedKey = replaceValue(entry.getKey(), inActionNamed);
                Object resolvedObject = resolveObject(entry.getValue(), inActionNamed);
                if (null != resolvedObject) {
                    resolvedMap.put(resolvedKey, resolvedObject);
                }
            }
            return resolvedMap;
        } catch (Exception e) {
            FlowConfigError configError = new FlowConfigError();
            configError.setMessage("Failed to resolve parameters: " + e.getMessage());
            configError.setErrorType(FlowErrorType.UNRESOLVED_VARIABLE);
            configError.setConfigName(inActionNamed);
            errors.add(configError);
            return params;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    Object resolveObject(Object object, String actionNamed) {
        if (null == object) {
            return null;
        }

        if (object instanceof Collection) {
            return replaceListPlaceholders((Collection) object, actionNamed);
        } else if (object instanceof Map) {
            return replaceMapPlaceholders((Map<String, Object>) object, actionNamed);
        }

        return resolvePrimitive(object, actionNamed);
    }

    Collection<Object> replaceListPlaceholders(Collection<Object> objects, String actionNamed) {
        if (null == objects || objects.isEmpty()) {
            return objects;
        }

        Collection<Object> processed = new ArrayList<>();
        for (Object object : objects) {
            Object resolved = resolveObject(object, actionNamed);
            if (null != resolved) {
                processed.add(resolved);
            }
        }
        return processed;
    }

    /**
     * At this point the object is known to be a string or primitive wrapper whose value
     * can be replaced directly.
     * @param object that has a string value that should be resolved
     * @param inActionNamed action that this value is being resolved for
     * @return replaced value if it was templated, original value if not or null if there was no value
     */
    Object resolvePrimitive(Object object, String inActionNamed) {
        String resolvedValue = replaceValue(object.toString(), inActionNamed);
        if (null != resolvedValue && !resolvedValue.isBlank()) {
            if (isArrayString(resolvedValue)) {
                return readStringAsList(resolvedValue);
            } else if (isMapString(resolvedValue)) {
                return VariableDataType.readStringAsMap(stripWrappers(resolvedValue));
            } else {
                return resolvedValue;
            }
        }
        return null;
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

    public static boolean isArrayString(String value) {
        return value.startsWith("[") && value.endsWith("]");
    }

    public static boolean isMapString(String value) {
        return value.startsWith("{") && value.endsWith("}");
    }

    /**
     * Takes in a comma seperated list in a string and splits it into a list of strings.
     * Each value will be trimmed
     * @param value string containing a comma separated list that is wrapped in brackets
     * @return value split into a list
     */
    public static List<String> readStringAsList(String value) {
        if (value == null || StringUtils.isBlank(value) || "[]".equals(value)) {
            return List.of();
        }

        String[] splitValues = stripWrappers(value).split(",");
        List<String> results = new ArrayList<>();
        for (String splitValue: splitValues) {
            results.add(splitValue.trim());
        }

        return results;
    }

    public static String stripWrappers(String value) {
        return value.substring(1, value.length() - 1);
    }

}
