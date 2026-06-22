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
package org.deltafi.core.converters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import static org.assertj.core.api.Assertions.assertThat;

class DataSizeDeserializerTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new SimpleModule().addDeserializer(DataSize.class, new DataSizeDeserializer()))
            .build();

    @Test
    @DisplayName("parses standard DataSize string into Spring DataSize")
    void parsesStandardString() {
        DataSize size = mapper.readValue("\"10MB\"", DataSize.class);

        assertThat(size).isEqualTo(DataSize.parse("10MB"));
    }

    @Test
    @DisplayName("empty string deserializes to null")
    void emptyStringDeserializesToNull() {
        DataSize size = mapper.readValue("\"\"", DataSize.class);

        assertThat(size).isNull();
    }

    @Test
    @DisplayName("DataSize works on nested field")
    void deserializesNestedField() {
        Holder holder = mapper.readValue("{\"size\":\"2GB\"}", Holder.class);

        assertThat(holder.size).isEqualTo(DataSize.parse("2GB"));
    }

    static class Holder {
        public DataSize size;
    }
}
