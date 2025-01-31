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
package org.deltafi.common.util;

import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class ParameterUtilTest {

    @SneakyThrows
    @Test
    void testHasTemplates() {
        Map<String, Object> params = new HashMap<>();
        Assertions.assertThat(ParameterUtil.hasTemplates(params)).isFalse();

        params.put("foo", "bar");
        Assertions.assertThat(ParameterUtil.hasTemplates(params)).isFalse();

        params.put("foo", "{{ bar }}");
        Assertions.assertThat(ParameterUtil.hasTemplates(params)).isTrue();
    }

    @Test
    void testHasTemplate() throws ParameterTemplateException {
        Assertions.assertThat(ParameterUtil.hasTemplate("{{ test }}")).isTrue();
    }

    @Test
    @Disabled("TODO - enable this test if escaping support is added")
    void testHasTemplateEscaped() throws ParameterTemplateException {
        Assertions.assertThat(ParameterUtil.hasTemplate("\\{{ test }}")).isFalse();
    }
}