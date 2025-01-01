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
package org.deltafi.core.converters;

import org.assertj.core.api.Assertions;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;

class VariablePlaceholderHelperTest {

    private static final String PLACEHOLDER = "${key}";
    private static final String SET_VALUE = "setValue";
    private static final String DEFAULT_VALUE = "defaultValue";

    @ParameterizedTest
    @MethodSource("testArgs")
    void resolveTest(String placeholder, List<Variable> variables, String expected) {
        VariablePlaceholderHelper resolver = new VariablePlaceholderHelper(variables);
        String result = resolver.valueFromVariable(resolver.matchingVariable(placeholder, placeholder));
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("testArgsThatFail")
    void testResolveExceptions(String placeholder, List<Variable> variables) {
        VariablePlaceholderHelper resolver = new VariablePlaceholderHelper(variables);
        Assertions.assertThatThrownBy(() -> resolver.matchingVariable(placeholder, placeholder))
                                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testRequiredUnset() {
        VariablePlaceholderHelper resolver = new VariablePlaceholderHelper(List.of(Variable.builder().name(PLACEHOLDER).value(null).defaultValue(null).required(true).dataType(VariableDataType.STRING).build()));
        Variable variable = resolver.matchingVariable(PLACEHOLDER, PLACEHOLDER);
        Assertions.assertThatThrownBy(() -> resolver.valueFromVariable(variable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Found required variable ${key} without a value set");
    }

    private static List<Arguments> testArgs() {
        return List.of(
                Arguments.of(PLACEHOLDER, List.of(Variable.builder().name(PLACEHOLDER).value(SET_VALUE).defaultValue(DEFAULT_VALUE).dataType(VariableDataType.STRING).build()), SET_VALUE),
                Arguments.of(PLACEHOLDER, List.of(Variable.builder().name(PLACEHOLDER).value(null).defaultValue(DEFAULT_VALUE).dataType(VariableDataType.STRING).build()), DEFAULT_VALUE),
                Arguments.of(PLACEHOLDER, List.of(Variable.builder().name(PLACEHOLDER).value(null).defaultValue(null).dataType(VariableDataType.STRING).build()), ""),
                Arguments.of(PLACEHOLDER, List.of(Variable.builder().name(PLACEHOLDER).value(null).defaultValue(null).dataType(VariableDataType.LIST).build()), ""),
                Arguments.of(PLACEHOLDER, List.of(Variable.builder().name(PLACEHOLDER).value("").defaultValue(null).dataType(VariableDataType.LIST).build()), "[]"));
    }

    private static List<Arguments> testArgsThatFail() {
        return List.of(
                Arguments.of("${unresolved}", List.of(Variable.builder().name(PLACEHOLDER).value(SET_VALUE).defaultValue(DEFAULT_VALUE).dataType(VariableDataType.STRING).build()), null),
                Arguments.of(PLACEHOLDER, Collections.emptyList(), null),
                Arguments.of(PLACEHOLDER, null, null));
    }
}