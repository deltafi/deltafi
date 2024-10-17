/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.Getter;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

public class FlowPlanPropertyHelper {

    private final VariablePlaceholderHelper variablePlaceholderHelper;
    @Getter
    private final Set<FlowConfigError> errors;
    private final FlowPlanPropertyHelper maskedDelegate;

    public FlowPlanPropertyHelper(List<Variable> variables) {
        this(variables, variables != null && variables.stream().anyMatch(Variable::isMasked));
    }

    private FlowPlanPropertyHelper(List<Variable> variables, boolean createMaskedDelegate) {
        this.variablePlaceholderHelper = new VariablePlaceholderHelper(variables);
        this.errors = new HashSet<>();

        FlowPlanPropertyHelper maybeDelegate = null;
        if (createMaskedDelegate) {
            List<Variable> maskedVariables = variables.stream().map(Variable::maskIfSensitive).toList();
            maybeDelegate = new FlowPlanPropertyHelper(maskedVariables, false);
        }
        this.maskedDelegate = maybeDelegate;
    }

    public String getReplacedName(ActionConfiguration actionTemplate) {
        return replaceValueAsString(actionTemplate.getName(), actionTemplate.getName());
    }

    public <C extends ActionConfiguration> void replaceCommonActionPlaceholders(C actionConfiguration, ActionConfiguration actionTemplate) {
        actionConfiguration.setInternalParameters(replaceMapPlaceholders(actionTemplate.getParameters(), actionConfiguration.getName()));
        actionConfiguration.setParameters(maskedDelegate().orElse(this).replaceMapPlaceholders(actionTemplate.getParameters(), actionConfiguration.getName()));

        actionConfiguration.setJoin(actionTemplate.getJoin());
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

                String resolvedKey = replaceValueAsString(entry.getKey(), inActionNamed);
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
        return switch (object) {
            case null -> null;
            case Collection collectionObject -> replaceListPlaceholders(collectionObject, actionNamed);
            case Map mapObject -> replaceMapPlaceholders(mapObject, actionNamed);
            default -> resolvePrimitive(object, actionNamed);
        };
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
        ResolvedPlaceholder resolvedValue = replaceValue(object.toString(), inActionNamed);
        if (null != resolvedValue && !resolvedValue.result().isBlank()) {
            return resolvedValue.dataType().convertValue(resolvedValue.result());
        }
        return null;
    }

    public String replaceValueAsString(String value, String inActionNamed) {
        ResolvedPlaceholder resolvedPlaceholder = replaceValue(value, inActionNamed);
        return resolvedPlaceholder != null ? resolvedPlaceholder.result() : null;
    }

    public ResolvedPlaceholder replaceValue(String value, String inActionNamed) {
        try {
            return nonNull(value) ? variablePlaceholderHelper.replacePlaceholders(value) : null;
        } catch (IllegalArgumentException e) {
            FlowConfigError configError = new FlowConfigError();
            configError.setConfigName(inActionNamed);
            configError.setErrorType(FlowErrorType.UNRESOLVED_VARIABLE);
            configError.setMessage(e.getMessage());
            errors.add(configError);
        }

        // return the original value that has no corresponding variable, a config error is added above
        return new ResolvedPlaceholder(value, VariableDataType.STRING);
    }

    public Set<Variable> getAppliedVariables() {
        return variablePlaceholderHelper.getAppliedVariables().stream().map(Variable::maskIfSensitive).collect(Collectors.toSet());
    }

    private Optional<FlowPlanPropertyHelper> maskedDelegate() {
        return Optional.ofNullable(maskedDelegate);
    }

}
