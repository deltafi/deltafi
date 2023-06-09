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
package org.deltafi.core.converters;

import org.assertj.core.api.Assertions;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.common.types.Variable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;

class VariablePlaceholderResolverTest {

    private static final String PLACEHOLDER = "${key}";
    private static final String SET_VALUE = "setValue";
    private static final String DEFAULT_VALUE = "defaultValue";

    @ParameterizedTest
    @MethodSource("testArgs")
    void resolveTest(String placeholder, List<Variable> variables, String expected) {
        VariablePlaceholderResolver resolver = new VariablePlaceholderResolver(variables);

        String result = resolver.resolvePlaceholder(placeholder);

        Assertions.assertThat(result).isEqualTo(expected);
    }

    private static List<Arguments> testArgs() {
        return List.of(
        Arguments.of(PLACEHOLDER, List.of(Variable.newBuilder().name(PLACEHOLDER).value(SET_VALUE).defaultValue(DEFAULT_VALUE).dataType(VariableDataType.STRING).build()), SET_VALUE),
        Arguments.of(PLACEHOLDER, List.of(Variable.newBuilder().name(PLACEHOLDER).value(null).defaultValue(DEFAULT_VALUE).dataType(VariableDataType.STRING).build()), DEFAULT_VALUE),
        Arguments.of(PLACEHOLDER, List.of(Variable.newBuilder().name(PLACEHOLDER).value(null).defaultValue(null).dataType(VariableDataType.STRING).build()), ""),
        Arguments.of(PLACEHOLDER, List.of(Variable.newBuilder().name(PLACEHOLDER).value(null).defaultValue(null).dataType(VariableDataType.LIST).build()), ""),
        Arguments.of(PLACEHOLDER, List.of(Variable.newBuilder().name(PLACEHOLDER).value("").defaultValue(null).dataType(VariableDataType.LIST).build()), "[]"),
        Arguments.of(PLACEHOLDER, List.of(Variable.newBuilder().name(PLACEHOLDER).value(null).defaultValue(null).required(true).dataType(VariableDataType.STRING).build()), null),
        Arguments.of("${unresolved}", List.of(Variable.newBuilder().name(PLACEHOLDER).value(SET_VALUE).defaultValue(DEFAULT_VALUE).dataType(VariableDataType.STRING).build()), null),
        Arguments.of(PLACEHOLDER, Collections.emptyList(), null),
        Arguments.of(PLACEHOLDER, null, null));
    }

}