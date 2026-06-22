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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Jackson 3 leniency configuration of {@link JsonYamlMapper} — JSON or YAML
 * {@code null} mapped to a Java primitive field must produce the zero value rather than
 * fail, mirroring the Jackson 2 behavior that flow definitions relied on.
 */
class JsonYamlMapperLeniencyTest {

    @Test
    @DisplayName("YAML null on a primitive boolean field deserializes to false")
    void yamlNullPrimitiveBoolean() throws IOException {
        String yaml = "flag: null\n";

        try (InputStream in = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))) {
            PrimitiveHolder holder = JsonYamlMapper.readValue(in, "doc.yaml", PrimitiveHolder.class);
            assertThat(holder.flag).isFalse();
        }
    }

    @Test
    @DisplayName("YAML null on a primitive int field deserializes to zero")
    void yamlNullPrimitiveInt() throws IOException {
        String yaml = "count: null\n";

        try (InputStream in = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))) {
            PrimitiveHolder holder = JsonYamlMapper.readValue(in, "doc.yaml", PrimitiveHolder.class);
            assertThat(holder.count).isZero();
        }
    }

    @Test
    @DisplayName("JSON null on a primitive boolean field deserializes to false")
    void jsonNullPrimitiveBoolean() throws IOException {
        String json = "{\"flag\": null}";

        try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            PrimitiveHolder holder = JsonYamlMapper.readValue(in, "doc.json", PrimitiveHolder.class);
            assertThat(holder.flag).isFalse();
        }
    }

    @Test
    @DisplayName("JSON null on a primitive int field deserializes to zero")
    void jsonNullPrimitiveInt() throws IOException {
        String json = "{\"count\": null}";

        try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            PrimitiveHolder holder = JsonYamlMapper.readValue(in, "doc.json", PrimitiveHolder.class);
            assertThat(holder.count).isZero();
        }
    }

    static class PrimitiveHolder {
        public boolean flag;
        public int count;
    }
}
