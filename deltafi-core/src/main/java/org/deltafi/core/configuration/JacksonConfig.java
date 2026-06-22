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
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;
import org.deltafi.core.converters.DataSizeDeserializer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.DeserializationFeature;

@Configuration
public class JacksonConfig {
    @Bean
    public JacksonModule dataSizeModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(DataSize.class, new DataSizeDeserializer());
        return module;
    }

    /**
     * Restores Jackson 2 leniency on the inbound HTTP {@code JsonMapper} so older Go
     * and Python plugins keep registering: JSON {@code null} maps to a Java primitive's
     * zero value rather than failing the request, and unknown properties are ignored.
     */
    @Bean
    public JsonMapperBuilderCustomizer deltafiLenientJsonMapperBuilderCustomizer() {
        return builder -> builder
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
}