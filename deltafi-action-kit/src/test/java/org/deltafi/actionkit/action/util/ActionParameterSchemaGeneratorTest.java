/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.action.util;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionParameterSchemaGeneratorTest {
    @Test
    void testGetSchema() throws IOException {
        JsonNode schemaJson = ActionParameterSchemaGenerator.generateSchema(TestActionParameters.class);
        String expectedSchema = new String(Objects.requireNonNull(getClass().getResourceAsStream("/expectedParamSchema.json")).readAllBytes());
        assertEquals(expectedSchema, schemaJson.toPrettyString());
    }

    private static class ComplexParam {
        @JsonPropertyDescription("first field")
        @SuppressWarnings("unused")
        String firstField;

        @JsonPropertyDescription("first field")
        @SuppressWarnings("unused")
        String secondField;
    }

    private static class TestActionParameters extends ActionParameters {
        @lombok.Getter
        @JsonPropertyDescription("my great property")
        String parameter;

        @lombok.Getter
        @JsonPropertyDescription("complex type should not allow additional properties")
        ActionParameterSchemaGeneratorTest.ComplexParam complexParam;
    }

}