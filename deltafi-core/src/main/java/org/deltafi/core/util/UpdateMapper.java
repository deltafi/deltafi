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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import org.deltafi.core.exceptions.InvalidRequestException;

public class UpdateMapper {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .build();

    private UpdateMapper() {}

    public static <T> T readValue(String value, Class<T> clazz) {
        if (value == null) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readValue(value, clazz);
        } catch (UnrecognizedPropertyException e) {
            throw new InvalidRequestException("Unknown field '" + e.getPropertyName() + "'");
        } catch (JacksonException e) {
            throw new InvalidRequestException("Failed to parse JSON '" + value + "'");
        }
    }
}
