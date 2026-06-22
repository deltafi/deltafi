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
package org.deltafi.core.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link JacksonConfig} restores Jackson 2 leniency on the inbound HTTP
 * {@code JsonMapper} so plugins keep registering after the Jackson 3 migration.
 */
class JacksonConfigTest {

    private final JacksonConfig config = new JacksonConfig();

    @Test
    @DisplayName("customizer disables FAIL_ON_UNKNOWN_PROPERTIES and FAIL_ON_NULL_FOR_PRIMITIVES")
    void customizerDisablesStrictFeatures() {
        JsonMapper.Builder builder = JsonMapper.builder();
        JsonMapperBuilderCustomizer customizer = config.deltafiLenientJsonMapperBuilderCustomizer();
        customizer.customize(builder);
        ObjectMapper mapper = builder.build();

        assertThat(mapper.deserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(mapper.deserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)).isFalse();
    }

    @Test
    @DisplayName("customizer-built mapper deserializes JSON null into a primitive boolean as false")
    void customizerAllowsNullForPrimitiveBoolean() {
        ObjectMapper mapper = buildLenientMapper();

        PrimitiveHolder holder = mapper.readValue("{\"someBool\": null}", PrimitiveHolder.class);

        assertThat(holder.someBool).isFalse();
    }

    @Test
    @DisplayName("customizer-built mapper deserializes JSON null into a primitive int as zero")
    void customizerAllowsNullForPrimitiveInt() {
        ObjectMapper mapper = buildLenientMapper();

        PrimitiveHolder holder = mapper.readValue("{\"someInt\": null}", PrimitiveHolder.class);

        assertThat(holder.someInt).isZero();
    }

    @Test
    @DisplayName("customizer-built mapper ignores unknown JSON properties")
    void customizerIgnoresUnknownProperties() {
        ObjectMapper mapper = buildLenientMapper();

        PrimitiveHolder holder = mapper.readValue("{\"someBool\": true, \"phantom\": 42}", PrimitiveHolder.class);

        assertThat(holder.someBool).isTrue();
    }

    @Test
    @DisplayName("dataSizeModule bean registers the DataSize deserializer")
    void dataSizeModuleRegistersDeserializer() {
        JacksonModule module = config.dataSizeModule();
        ObjectMapper mapper = JsonMapper.builder().addModule(module).build();

        DataSize size = mapper.readValue("\"10MB\"", DataSize.class);

        assertThat(size).isEqualTo(DataSize.parse("10MB"));
    }

    private ObjectMapper buildLenientMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        config.deltafiLenientJsonMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }

    static class PrimitiveHolder {
        public boolean someBool;
        public int someInt;
    }
}
