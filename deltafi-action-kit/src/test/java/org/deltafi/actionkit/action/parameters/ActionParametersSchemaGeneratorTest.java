/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.action.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import lombok.Getter;
import org.deltafi.actionkit.ActionKitAutoConfiguration;
import org.deltafi.actionkit.action.parameters.annotation.Size;
import org.deltafi.common.resource.Resource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionParametersSchemaGeneratorTest {
    @Test
    void testGetSchema() throws IOException {
        ActionKitAutoConfiguration configuration = new ActionKitAutoConfiguration(null);
        SchemaGenerator generator  = configuration.parametersSchemaGenerator(Optional.empty());
        JsonNode schemaJson = generator.generateSchema(TestActionParameters.class);
        String expectedSchema = new String(Objects.requireNonNull(getClass().getResourceAsStream("/expectedParamSchema.json")).readAllBytes());
        assertEquals(Resource.read("/expectedParamSchema.json"), schemaJson.toPrettyString());
    }

    @SuppressWarnings("unused")
    private static class ComplexParam {
        @JsonPropertyDescription("first field")
        @SuppressWarnings("unused")
        String firstField;

        @JsonPropertyDescription("first field")
        String secondField;
    }

    private enum TestEnum {
        A, B, C
    }

    @AllArgsConstructor
    @Getter
    private enum Format {
        @JsonProperty("tar") TAR("tar", "application/x-tar"),
        @JsonProperty("zip") ZIP("zip", "application/zip");

        private final String value;
        private final String mediaType;
    }

    @Getter
    private static class TestActionParameters extends ActionParameters {
        @JsonProperty(defaultValue = "true")
        boolean booleanParameter = true;

        TestEnum enumParameter = TestEnum.B;

        @JsonProperty(required = true)
        @JsonPropertyDescription("Format to compress to")
        Format formatParameter;

        @JsonProperty(defaultValue = "defaultString")
        @JsonPropertyDescription("my great property")
        @Size(maxLength = 5000)
        String parameter = "defaultString";

        @JsonProperty(required = true, defaultValue = "[\"abc\",\"123\"]")
        @JsonPropertyDescription("my great list")
        List<String> listParameter = List.of("abc", "123");

        @JsonPropertyDescription("A list of tags to assign to the fetched content.")
        Set<String> tags;

        @JsonPropertyDescription("Key value pairs to be added")
        Map<String, Integer> mapParameter;

        @JsonProperty(required = true)
        @JsonPropertyDescription("complex type should not allow additional properties")
        ActionParametersSchemaGeneratorTest.ComplexParam complexParam;
    }
}