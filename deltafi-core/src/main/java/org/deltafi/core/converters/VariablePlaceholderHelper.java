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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.jetbrains.annotations.NotNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Based on code from {@link org.springframework.util.PropertyPlaceholderHelper}, returns
 * both the resolved value and the datatype of the variable used to resolve the value.
 */
@Slf4j
public class VariablePlaceholderHelper {

    private static final String PLACEHOLDER_PREFIX = "${";
    private static final String PLACEHOLDER_SUFFIX = "}";
    private static final String SIMPLE_PREFIX = "{";

    private final List<Variable> variables;
    private final Set<Variable> appliedVariables = new HashSet<>();

    /**
     * Construct a new VariablePlaceholderHelper with the list of variables
     * that will be used to resolve placeholders
     * @param variables list of variables to search for placeholder values
     */
    public VariablePlaceholderHelper(List<Variable> variables) {
        this.variables = Objects.requireNonNullElse(variables, List.of());
    }


    /**
     * Get the set of variables that were used to resolve placeholders
     * @return the set of variables that were used to resolve placeholders
     */
    public Set<Variable> getAppliedVariables() {
        return appliedVariables;
    }

    /**
     * Replaces all placeholders of format {@code ${name}} with the value returned
     * from the supplied {@link Variable} list.
     * @param value the value containing the placeholders to be replaced
     * @return the supplied value with placeholders replaced inline
     */
    public ResolvedPlaceholder replacePlaceholders(String value) {
        Assert.notNull(value, "'value' must not be null");
        return parseStringValue(value, null);
    }

    protected ResolvedPlaceholder parseStringValue(String value, @Nullable Set<String> visitedPlaceholders) {
        int startIndex = value.indexOf(PLACEHOLDER_PREFIX);
        if (startIndex == -1) {
            return new ResolvedPlaceholder(value, VariableDataType.STRING);
        }

        VariableDataType dataType = null;
        StringBuilder result = new StringBuilder(value);
        while (startIndex != -1) {
            int endIndex = findPlaceholderEndIndex(result, startIndex);
            if (endIndex != -1) {
                String placeholder = result.substring(startIndex + PLACEHOLDER_PREFIX.length(), endIndex);
                String originalPlaceholder = placeholder;
                if (visitedPlaceholders == null) {
                    visitedPlaceholders = new HashSet<>(4);
                }
                if (!visitedPlaceholders.add(originalPlaceholder)) {
                    throw new IllegalArgumentException(
                            "Circular placeholder reference '" + originalPlaceholder + "' in property definitions");
                }
                // Recursive invocation, parsing placeholders contained in the placeholder key.
                var recursivePlaceholder = parseStringValue(placeholder, visitedPlaceholders);
                placeholder = recursivePlaceholder.result();

                // Now obtain the value for the fully resolved key...
                Variable variable = matchingVariable(placeholder, value);
                String propVal = valueFromVariable(variable);

                // if multiple variables are used to resolve a placeholder fall back to using a STRING
                dataType = dataType == null ? variable.getDataType() : VariableDataType.STRING;
                result.replace(startIndex, endIndex + PLACEHOLDER_SUFFIX.length(), propVal);
                log.trace("Resolved placeholder '{}'", placeholder);
                startIndex = result.indexOf(PLACEHOLDER_PREFIX, startIndex + propVal.length());
                visitedPlaceholders.remove(originalPlaceholder);
            }
            else {
                startIndex = -1;
            }
        }
        return new ResolvedPlaceholder(result.toString(), dataType);
    }

    private int findPlaceholderEndIndex(CharSequence buf, int startIndex) {
        int index = startIndex + PLACEHOLDER_PREFIX.length();
        int withinNestedPlaceholder = 0;
        while (index < buf.length()) {
            if (StringUtils.substringMatch(buf, index, PLACEHOLDER_SUFFIX)) {
                if (withinNestedPlaceholder > 0) {
                    withinNestedPlaceholder--;
                    index = index + PLACEHOLDER_SUFFIX.length();
                } else {
                    return index;
                }
            } else if (StringUtils.substringMatch(buf, index, SIMPLE_PREFIX)) {
                withinNestedPlaceholder++;
                index = index + SIMPLE_PREFIX.length();
            } else {
                index++;
            }
        }
        return -1;
    }

    Variable matchingVariable(@NotNull String placeholderName, String fullValue) {
        return variables.stream()
                .filter(variable -> variable.getName().equals(placeholderName))
                .findFirst()
                .orElseThrow(() -> this.missingVariable(placeholderName, fullValue));
    }

    String valueFromVariable(Variable variable) {
        String value = Objects.isNull(variable.getValue()) ? variable.getDefaultValue() : variable.getValue();

        if (null == value) {
            if (variable.isRequired()) {
                throw new IllegalArgumentException("Found required variable " + variable.getName() + " without a value set");
            }
            // Return an empty string that will be pruned
            return "";
        }

        return variable.getDataType().formatString(value);
    }

    private IllegalArgumentException missingVariable(@NotNull String placeholderName, String fullValue) {
        return new IllegalArgumentException("Could not find a variable named '" +
                placeholderName + "'" + " used in value \"" + fullValue + "\"");
    }
}
