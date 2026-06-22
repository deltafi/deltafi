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
package org.deltafi.core.util;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import org.deltafi.core.types.DeltaFileDTO;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class DeltaFileMapper {
    public static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new SimpleModule().addSerializer(OffsetDateTime.class, new ValueSerializer<>() {
                @Override
                public void serialize(OffsetDateTime offsetDateTime, JsonGenerator jsonGenerator, SerializationContext serializerProvider) {
                    jsonGenerator.writeString(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC).format(offsetDateTime));
                }
            }))
            .build();

    public static byte[] toJsonBytes(Object object) throws IOException {
        return OBJECT_MAPPER.writeValueAsBytes(object);
    }

    public static DeltaFileDTO readJsonBytes(byte[] bytes) throws IOException {
        return OBJECT_MAPPER.readValue(bytes, DeltaFileDTO.class);
    }
}
