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
package org.deltafi.common.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class SpringDataSizeModuleTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new SpringDataSizeModule())
            .build();

    @Test
    @DisplayName("deserializes Spring DataSize from string literal")
    void deserializesFromString() {
        DataSize size = mapper.readValue("\"10MB\"", DataSize.class);

        assertThat(size).isEqualTo(DataSize.parse("10MB"));
    }

    @Test
    @DisplayName("deserializes Spring DataSize on a nested field")
    void deserializesNestedField() {
        Holder holder = mapper.readValue("{\"size\":\"5KB\"}", Holder.class);

        assertThat(holder.size).isEqualTo(DataSize.parse("5KB"));
    }

    @Test
    @DisplayName("handles each supported unit string")
    void deserializesAllUnits() {
        assertThat(mapper.readValue("\"100B\"", DataSize.class)).isEqualTo(DataSize.parse("100B"));
        assertThat(mapper.readValue("\"2KB\"", DataSize.class)).isEqualTo(DataSize.parse("2KB"));
        assertThat(mapper.readValue("\"3MB\"", DataSize.class)).isEqualTo(DataSize.parse("3MB"));
        assertThat(mapper.readValue("\"4GB\"", DataSize.class)).isEqualTo(DataSize.parse("4GB"));
    }

    static class Holder {
        public DataSize size;
    }
}
