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
package org.deltafi.core.validation;

import org.deltafi.core.generated.types.FlowConfigError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct coverage for {@link SchemaComplianceUtil#validateParameters(String, Map, Map)}
 * after the migration from {@code JsonNode} to {@code Map<String, Object>} for the schema arg.
 * Verifies the boundary behaviors (empty schema map, empty params, valid + invalid params).
 */
class SchemaComplianceUtilTest {

    @Test
    @DisplayName("empty schema map accepts any parameter map without error")
    void emptySchemaAcceptsAnyParams() {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> params = Map.of("any", "value");

        Optional<FlowConfigError> result = SchemaComplianceUtil.validateParameters("act", schema, params);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("empty schema map with null params returns no error")
    void emptySchemaWithNullParams() {
        Optional<FlowConfigError> result = SchemaComplianceUtil.validateParameters("act", new HashMap<>(), null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("schema with required field reports missing parameter")
    void missingRequiredField() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "required", java.util.List.of("url"),
                "properties", Map.of("url", Map.of("type", "string")));

        Optional<FlowConfigError> result = SchemaComplianceUtil.validateParameters("act", schema, new HashMap<>());

        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("'url'");
    }

    @Test
    @DisplayName("matching parameter passes schema validation")
    void matchingParameterIsValid() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "required", java.util.List.of("url"),
                "properties", Map.of("url", Map.of("type", "string")));
        Map<String, Object> params = Map.of("url", "https://example.org");

        Optional<FlowConfigError> result = SchemaComplianceUtil.validateParameters("act", schema, params);

        assertThat(result).isEmpty();
    }
}
