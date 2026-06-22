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
package org.deltafi.common.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionConfigurationTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("parameterSchema Map roundtrips through JSON preserving nested structure")
    void parameterSchemaRoundtrip() {
        Map<String, Object> nestedProperty = new LinkedHashMap<>();
        nestedProperty.put("type", "string");
        nestedProperty.put("minLength", 1);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", nestedProperty);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "http://json-schema.org/draft-07/schema#");
        schema.put("type", "object");
        schema.put("required", List.of("url"));
        schema.put("properties", properties);
        schema.put("additionalProperties", false);

        ActionConfiguration config = new ActionConfiguration("RestEgress", ActionType.EGRESS, "org.deltafi.core.action.RestEgress");
        config.setParameterSchema(schema);

        String json = mapper.writeValueAsString(config);
        ActionConfiguration roundtripped = mapper.readValue(json, ActionConfiguration.class);

        assertThat(roundtripped).isEqualTo(config);
        assertThat(roundtripped.getParameterSchema())
                .isNotNull()
                .containsEntry("type", "object")
                .containsEntry("additionalProperties", false);

        @SuppressWarnings("unchecked")
        Map<String, Object> roundtrippedProperties =
                (Map<String, Object>) roundtripped.getParameterSchema().get("properties");
        assertThat(roundtrippedProperties).containsKey("url");

        @SuppressWarnings("unchecked")
        Map<String, Object> roundtrippedUrl = (Map<String, Object>) roundtrippedProperties.get("url");
        assertThat(roundtrippedUrl)
                .containsEntry("type", "string")
                .containsEntry("minLength", 1);
    }

    @Test
    @DisplayName("parameterSchema is null when absent in JSON")
    void parameterSchemaNullByDefault() {
        ActionConfiguration config = new ActionConfiguration("RestEgress", ActionType.EGRESS, "org.deltafi.core.action.RestEgress");

        String json = mapper.writeValueAsString(config);
        ActionConfiguration roundtripped = mapper.readValue(json, ActionConfiguration.class);

        assertThat(roundtripped.getParameterSchema()).isNull();
    }

    @Test
    @DisplayName("empty parameterSchema Map survives JSON roundtrip")
    void parameterSchemaEmptyMapRoundtrip() {
        ActionConfiguration config = new ActionConfiguration("RestEgress", ActionType.EGRESS, "org.deltafi.core.action.RestEgress");
        config.setParameterSchema(new HashMap<>());

        String json = mapper.writeValueAsString(config);
        ActionConfiguration roundtripped = mapper.readValue(json, ActionConfiguration.class);

        assertThat(roundtripped.getParameterSchema()).isNotNull().isEmpty();
    }
}
