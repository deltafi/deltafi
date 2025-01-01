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
package org.deltafi.core.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TimeFormatterTest {

    private static Stream<Arguments> testDurationPretty() {
        return Stream.of(
                Arguments.of(Duration.ofSeconds(9), "9s"),
                Arguments.of(Duration.ofSeconds(60), "1m"),
                Arguments.of(Duration.ofSeconds(65), "1m5s"),
                Arguments.of(Duration.parse("PT1h3m5s"), "1h3m5s"),
                Arguments.of(Duration.parse("P3DT1h3m5s"), "3d1h3m5s")
            );
    }

    @ParameterizedTest
    @MethodSource
    void testDurationPretty(Duration duration, String expected) {
        assertThat(TimeFormatter.formattedDuration(duration)).isEqualTo(expected);
    }
}