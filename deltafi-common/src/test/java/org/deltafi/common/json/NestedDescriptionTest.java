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
package org.deltafi.common.json;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import tools.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ABOUTME: Tests that JSON schema generation preserves descriptions for nested parameter structures.
 * ABOUTME: Verifies @JsonPropertyDescription annotations propagate through nested objects and lists.
 */
class NestedDescriptionTest {

    private SchemaGenerator generator;

    @BeforeEach
    void setUp() {
        generator  = SchemaGeneratorFactory.buildSchemaGenerator(null);
    }

    @Test
    void nestedObjectDescriptionsAreIncludedInSchema() {
        JsonNode schema = generator.generateSchema(ParamsWithNestedObject.class);
        JsonNode properties = schema.get("properties");

        // Top-level field should have description
        assertEquals("The endpoint configuration", properties.get("endpoint").get("description").asText());

        // Nested object's fields should have descriptions
        JsonNode endpointProps = properties.get("endpoint").get("properties");
        assertEquals("The URL to connect to", endpointProps.get("url").get("description").asText());
        assertEquals("Timeout in milliseconds", endpointProps.get("timeoutMs").get("description").asText());
    }

    @Test
    void nestedListDescriptionsAreIncludedInSchema() {
        JsonNode schema = generator.generateSchema(ParamsWithNestedList.class);
        JsonNode properties = schema.get("properties");

        // Top-level list field should have description
        assertEquals("List of endpoints to try", properties.get("endpoints").get("description").asText());

        // Items within the list should have their field descriptions
        JsonNode itemProps = properties.get("endpoints").get("items").get("properties");
        assertEquals("The URL to connect to", itemProps.get("url").get("description").asText());
        assertEquals("Timeout in milliseconds", itemProps.get("timeoutMs").get("description").asText());
    }

    @Test
    void deeplyNestedDescriptionsAreIncludedInSchema() {
        JsonNode schema = generator.generateSchema(ParamsWithDeeplyNested.class);
        JsonNode properties = schema.get("properties");

        // Top level
        JsonNode outerProps = properties.get("outer").get("properties");
        assertEquals("The inner configuration", outerProps.get("inner").get("description").asText());

        // Second level
        JsonNode innerProps = outerProps.get("inner").get("properties");
        assertEquals("The deepest value", innerProps.get("deepValue").get("description").asText());
    }

    // Test parameter classes

    @Data
    @EqualsAndHashCode(callSuper = false)
    private static class ParamsWithNestedObject {
        @JsonPropertyDescription("The endpoint configuration")
        private EndpointConfig endpoint;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    private static class ParamsWithNestedList {
        @JsonPropertyDescription("List of endpoints to try")
        private List<EndpointConfig> endpoints;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    private static class ParamsWithDeeplyNested {
        @JsonPropertyDescription("The outer configuration")
        private OuterConfig outer;
    }

    @Data
    private static class EndpointConfig {
        @JsonPropertyDescription("The URL to connect to")
        private String url;

        @JsonPropertyDescription("Timeout in milliseconds")
        private int timeoutMs;
    }

    @Data
    private static class OuterConfig {
        @JsonPropertyDescription("The inner configuration")
        private InnerConfig inner;
    }

    @Data
    private static class InnerConfig {
        @JsonPropertyDescription("The deepest value")
        private String deepValue;
    }
}
