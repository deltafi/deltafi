package org.deltafi.actionkit.action.util;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionParameterSchemaGeneratorTest {

    @Test
    void testGetSchema() throws IOException {
        JsonNode schemaJson = ActionParameterSchemaGenerator.generateSchema(TestActionParameters.class);
        String expectedSchema = new String(getClass().getResourceAsStream("/expectedParamSchema.json").readAllBytes());
        assertEquals(expectedSchema, schemaJson.toPrettyString());
    }

    private static class ComplexParam {
        @JsonPropertyDescription("first field")
        String firstField;

        @JsonPropertyDescription("first field")
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