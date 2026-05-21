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
package org.deltafi.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSizeTest {

    @Test
    void parsesBareBytes() {
        assertThat(DataSize.parse("100").toBytes()).isEqualTo(100L);
        assertThat(DataSize.parse("100B").toBytes()).isEqualTo(100L);
    }

    @Test
    void parsesAllSupportedUnits() {
        assertThat(DataSize.parse("1KB").toBytes()).isEqualTo(1024L);
        assertThat(DataSize.parse("1MB").toBytes()).isEqualTo(1024L * 1024L);
        assertThat(DataSize.parse("1GB").toBytes()).isEqualTo(1024L * 1024L * 1024L);
        assertThat(DataSize.parse("1TB").toBytes()).isEqualTo(1024L * 1024L * 1024L * 1024L);
    }

    @Test
    void parseIsCaseInsensitiveAndIgnoresInternalWhitespace() {
        assertThat(DataSize.parse(" 10 mb ").toBytes()).isEqualTo(10L * 1024L * 1024L);
        assertThat(DataSize.parse("10Mb").toBytes()).isEqualTo(10L * 1024L * 1024L);
    }

    @Test
    void factoriesProduceExpectedByteCounts() {
        assertThat(DataSize.ofGigabytes(8).toBytes()).isEqualTo(8L * 1024L * 1024L * 1024L);
        assertThat(DataSize.ofKilobytes(2).toBytes()).isEqualTo(2048L);
    }

    @Test
    void conversionAccessorsAreFloorDivisions() {
        DataSize twoAndAHalfMb = DataSize.ofBytes((long) (2.5 * 1024 * 1024));
        assertThat(twoAndAHalfMb.toMegabytes()).isEqualTo(2L);
        assertThat(twoAndAHalfMb.toKilobytes()).isEqualTo(2560L);
    }

    @Test
    void equalityAndComparisonAreByByteCount() {
        assertThat(DataSize.parse("1MB")).isEqualTo(DataSize.ofKilobytes(1024));
        assertThat(DataSize.ofBytes(10).compareTo(DataSize.ofBytes(20))).isNegative();
    }

    @Test
    void invalidStringsRaiseIllegalArgument() {
        assertThatThrownBy(() -> DataSize.parse("10XB"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DataSize.parse("notanumber"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
