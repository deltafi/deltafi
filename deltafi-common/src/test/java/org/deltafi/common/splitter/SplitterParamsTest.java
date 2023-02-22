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
package org.deltafi.common.splitter;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SplitterParamsTest {

    @Test
    void testDefaults() {
        SplitterParams expected = new SplitterParams(null, false, Integer.MAX_VALUE, 524288000);
        Assertions.assertThat(SplitterParams.builder().build()).isEqualTo(expected);
    }

    @Test
    void testMaxSizeValidation() {
        SplitterParams.SplitterParamsBuilder builder = SplitterParams.builder();
        Assertions.assertThatNoException().isThrownBy(() -> builder.build());
        Assertions.assertThatThrownBy(() -> builder.maxSize(0).build()).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatNoException().isThrownBy(() -> builder.maxSize(1).build());

    }

    @Test
    void testMaxRowValidation() {
        SplitterParams.SplitterParamsBuilder builder = SplitterParams.builder();
        Assertions.assertThatNoException().isThrownBy(() -> builder.build());
        Assertions.assertThatThrownBy(() -> builder.maxRows(0).build()).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatNoException().isThrownBy(() -> builder.maxRows(1).build());
    }
}