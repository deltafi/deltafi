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
package org.deltafi.common.types;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class VariableDataTypeTest {

    @ParameterizedTest
    @MethodSource
    void readStringAsList(String input, List<String> expected) {
        List<String> result = VariableDataType.readStringAsList(input);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource
    void readStringAsMap(String input, Map<String, Object> expected) {
        Map<Object, Object> result = VariableDataType.readStringAsMap(input);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource
    void invalidMapInput(String input, String expectedMessage) {
        Assertions.assertThatThrownBy(() -> VariableDataType.readStringAsMap(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    @Test
    void duplicateKey() {
        Assertions.assertThatThrownBy(() -> VariableDataType.readStringAsMap("a: b, a: c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate key a (attempted merging values b and c)");
    }

    private static Stream<Arguments> readStringAsList() {
        return Stream.of(
                Arguments.of(null, List.of()),
                Arguments.of("", List.of()),
                Arguments.of(",", List.of()),
                Arguments.of(",,,", List.of()),
                Arguments.of(" [   ]   ", List.of()),
                Arguments.of("[ \\, ]", List.of(",")),
                Arguments.of(" a, b\\,c, \\d\n, e\\,f ", List.of("a", "b,c", "\\d", "e,f")),
                Arguments.of("[ ab\\\\,next]", List.of("ab\\\\", "next"))
        );
    }

    private static Stream<Arguments> readStringAsMap() {
        return Stream.of(
                Arguments.of(null, Map.of()),
                Arguments.of("", Map.of()),
                Arguments.of("  {   }  ", Map.of()),
                Arguments.of(",,", Map.of()),
                Arguments.of("a: b,    c\\:d: e\\,f", Map.of("a", "b", "c:d", "e,f")),
                Arguments.of("a:,c:d", Map.of("a", "", "c", "d")),
                Arguments.of(":b,c:d", Map.of("", "b", "c", "d")),
                Arguments.of(",c:d,  ", Map.of("c", "d")),
                Arguments.of(",c:d,  ", Map.of("c", "d")),
                Arguments.of("a\\\\:b\\\tc", Map.of("a\\\\", "b\\\tc")),
                Arguments.of("a\\\\\\\\:b", Map.of("a\\\\\\\\", "b"))
        );
    }

    private static Stream<Arguments> invalidMapInput() {
        return Stream.of(
                Arguments.of("a:b:c", "The value 'a:b:c' contains an invalid key value pair, multiple delimiters found. The key value pair must be of the format key: value"),
                Arguments.of("abc", "The value 'abc' contains an invalid key value pair, no delimiters found. The key value pair must be of the format key: value"),
                Arguments.of("ab\\:c", "The value 'ab\\:c' contains an invalid key value pair, no delimiters found. The key value pair must be of the format key: value"),
                Arguments.of("ab\\\\\\:c", "The value 'ab\\\\\\:c' contains an invalid key value pair, no delimiters found. The key value pair must be of the format key: value")
        );
    }
}