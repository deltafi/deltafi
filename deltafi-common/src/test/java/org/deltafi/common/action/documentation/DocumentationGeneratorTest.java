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
package org.deltafi.common.action.documentation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.common.resource.Resource;
import org.deltafi.common.types.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

public class DocumentationGeneratorTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void generatesDocsSimple() throws IOException {
        ActionDescriptor actionDescriptor = ActionDescriptor.builder()
                .name("TestAction")
                .schema(Collections.emptyMap())
                .actionOptions(ActionOptions.builder()
                        .description("The description")
                        .inputSpec(ActionOptions.InputSpec.builder()
                                .contentSummary("The input content summary")
                                .metadataSummary("The input metadata summary")
                                .build())
                        .outputSpec(ActionOptions.OutputSpec.builder()
                                .contentSummary("The output content summary")
                                .metadataSummary("The output metadata summary")
                                .annotationsSummary("The output annotations summary")
                                .build())
                        .filters("Filter 1", "Filter 2")
                        .errors("Error 1", "Error 2")
                        .notes("Note 1", "Note 2")
                        .details("The details")
                        .build())
                .build();

        String docs = DocumentationGenerator.generateActionDocs(actionDescriptor);

        Assertions.assertEquals(Resource.read("/documentation-generator/expectedDocsSimple.md"), docs);
    }

    @Test
    void generatesDocsComplex() throws IOException {
        Map<String, Object> schema = OBJECT_MAPPER.readValue(Resource.read("/documentation-generator/paramSchema.json"),
                new TypeReference<>() {});

        ActionDescriptor actionDescriptor = ActionDescriptor.builder()
                .name("org.deltafi.core.action.test.TestAction")
                .type(ActionType.TRANSFORM)
                .supportsJoin(true)
                .schema(schema)
                .actionOptions(ActionOptions.builder()
                        .description("The description")
                        .inputSpec(ActionOptions.InputSpec.builder()
                                .contentSpecs(List.of(
                                        ActionOptions.ContentSpec.builder()
                                                .name("Content 1")
                                                .mediaType("Content 1 media type")
                                                .description("Content 1 description")
                                                .build(),
                                        ActionOptions.ContentSpec.builder()
                                                .name("Content 2")
                                                .mediaType("Content 2 media type")
                                                .description("Content 2 description")
                                                .build()))
                                .metadataDescriptions(List.of(
                                        ActionOptions.KeyedDescription.builder()
                                                .key("Metadata 1 key")
                                                .description("Metadata 1 description")
                                                .build(),
                                        ActionOptions.KeyedDescription.builder()
                                                .key("Metadata 2 key")
                                                .description("Metadata 2 description")
                                                .build()))
                                .build())
                        .outputSpec(ActionOptions.OutputSpec.builder()
                                .contentSpecs(List.of(
                                        ActionOptions.ContentSpec.builder()
                                                .name("Content 1")
                                                .mediaType("Content 1 media type")
                                                .description("Content 1 description")
                                                .build(),
                                        ActionOptions.ContentSpec.builder()
                                                .name("Content 2")
                                                .mediaType("Content 2 media type")
                                                .description("Content 2 description")
                                                .build()))
                                .metadataDescriptions(List.of(
                                        ActionOptions.KeyedDescription.builder()
                                                .key("Metadata 1 key")
                                                .description("Metadata 1 description")
                                                .build(),
                                        ActionOptions.KeyedDescription.builder()
                                                .key("Metadata 2 key")
                                                .description("Metadata 2 description")
                                                .build()))
                                .annotationDescriptions(List.of(
                                        ActionOptions.KeyedDescription.builder()
                                                .key("Annotation 1 key")
                                                .description("Annotation 1 description")
                                                .build(),
                                        ActionOptions.KeyedDescription.builder()
                                                .key("Annotation 2 key")
                                                .description("Annotation 2 description")
                                                .build()))
                                .build())
                        .filters(List.of(
                                new ActionOptions.DescriptionWithConditions("Filter 1",
                                        List.of("Condition A", "Condition B")),
                                new ActionOptions.DescriptionWithConditions("Filter 2",
                                        List.of("Condition C", "Condition D"))))
                        .errors(List.of(
                                new ActionOptions.DescriptionWithConditions("Error 1",
                                        List.of("Condition A", "Condition B")),
                                new ActionOptions.DescriptionWithConditions("Error 2",
                                        List.of("Condition C", "Condition D"))))
                        .notes("Note 1", "Note 2")
                        .details("The details")
                        .build())
                .build();

        String docs = DocumentationGenerator.generateActionDocs(actionDescriptor);

        Assertions.assertEquals(Resource.read("/documentation-generator/expectedDocsComplex.md"), docs);
    }

    @Test
    void generatesDocsPassthrough() throws IOException {
        ActionDescriptor actionDescriptor = ActionDescriptor.builder()
                .name("TestAction")
                .schema(Collections.emptyMap())
                .actionOptions(ActionOptions.builder()
                        .description("The description")
                        .outputSpec(ActionOptions.OutputSpec.builder()
                                .passthrough(true)
                                .build())
                        .build())
                .build();

        String docs = DocumentationGenerator.generateActionDocs(actionDescriptor);

        Assertions.assertEquals(Resource.read("/documentation-generator/expectedDocsPassthrough.md"), docs);
    }

    @Test
    void generatesDocsForPolymorphicListParameter() throws IOException {
        // a discriminated-union list parameter has items defined via anyOf with no top-level items.type;
        // the generator must not NPE and should describe the parameter as an object list
        String schemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "operations": {
                      "description": "Ordered list of operations",
                      "type": "array",
                      "items": {
                        "anyOf": [
                          { "type": "object", "properties": { "type": { "const": "ADD" }, "amount": { "type": "integer", "description": "How much to add" } }, "required": ["type"] },
                          { "type": "object", "properties": { "type": { "const": "DELETE" }, "key": { "type": "string", "description": "Key to delete" } }, "required": ["type"] }
                        ]
                      }
                    }
                  },
                  "additionalProperties": false
                }
                """;
        Map<String, Object> schema = OBJECT_MAPPER.readValue(schemaJson, new TypeReference<>() {});

        ActionDescriptor actionDescriptor = ActionDescriptor.builder()
                .name("org.deltafi.core.action.test.PolymorphicAction")
                .type(ActionType.TRANSFORM)
                .schema(schema)
                .actionOptions(ActionOptions.builder().description("The description").build())
                .build();

        String docs = DocumentationGenerator.generateActionDocs(actionDescriptor);

        Assertions.assertTrue(docs.contains("`operations`"), "expected the operations parameter to be documented");
        // the parameter links into the Types section, where each variant is keyed by its discriminator value
        Assertions.assertTrue(docs.contains("[`[operations]`](#operations)"),
                "expected the polymorphic list parameter to link to its type card");
        Assertions.assertTrue(docs.contains("## Types") && docs.contains("### operations"),
                "expected the union detailed in the Types section");
        Assertions.assertTrue(docs.contains("**ADD**"), "expected the ADD variant");
        Assertions.assertTrue(docs.contains("**DELETE**"), "expected the DELETE variant");
        Assertions.assertTrue(docs.contains("`amount`") && docs.contains("How much to add"),
                "expected the ADD variant's fields");
        Assertions.assertTrue(docs.contains("`key`") && docs.contains("Key to delete"),
                "expected the DELETE variant's fields");
        // the const 'type' discriminator selects the variant; it is not repeated as a field row
        Assertions.assertFalse(docs.contains("`type`"), "the discriminator should not appear as a field");
    }

    @Test
    void generatesDocsForNestedObjectParameter() throws IOException {
        String schemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "endpoint": {
                      "description": "Connection settings",
                      "type": "object",
                      "properties": {
                        "host": { "type": "string", "description": "Host name" },
                        "port": { "type": "integer", "description": "Port number" }
                      },
                      "required": ["host"]
                    }
                  },
                  "additionalProperties": false
                }
                """;
        Map<String, Object> schema = OBJECT_MAPPER.readValue(schemaJson, new TypeReference<>() {});

        ActionDescriptor actionDescriptor = ActionDescriptor.builder()
                .name("org.deltafi.core.action.test.NestedAction")
                .type(ActionType.TRANSFORM)
                .schema(schema)
                .actionOptions(ActionOptions.builder().description("The description").build())
                .build();

        String docs = DocumentationGenerator.generateActionDocs(actionDescriptor);

        Assertions.assertTrue(docs.contains("## Types") && docs.contains("### endpoint"),
                "expected the nested object detailed in the Types section");
        Assertions.assertTrue(docs.contains("[`endpoint`](#endpoint)"),
                "expected the parameter to link to its type card");
        Assertions.assertTrue(docs.contains("`host`") && docs.contains("`string`"),
                "expected the host field with its type");
        Assertions.assertTrue(docs.contains("`port`") && docs.contains("`integer`"),
                "expected the port field with its type");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i != -1; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    @Test
    void rendersNamedTypesOnceInTypesSection() throws IOException {
        // Structure2 and Structure1 are referenced from multiple parameters (mirrors HelloWorldTransformParameters).
        // Each named type is defined once in the Types section and linked by name, so reused types are not duplicated.
        String schemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "structure1": { "description": "Structure 1 instance", "$ref": "#/$defs/Structure1" },
                    "structure2": { "description": "Structure 2 instance", "$ref": "#/$defs/Structure2" },
                    "structure3List": { "description": "Structure3 List", "type": "array", "items": { "$ref": "#/$defs/Structure3" } }
                  },
                  "$defs": {
                    "Structure1": { "type": "object", "properties": {
                      "s1Boolean": { "type": "boolean", "description": "Description of s1Boolean" } } },
                    "Structure2": { "type": "object", "properties": {
                      "s2StringList": { "type": "array", "items": { "type": "string" }, "description": "Description of s2StringList" } } },
                    "Structure3": { "type": "object", "properties": {
                      "s2InstanceInS3": { "description": "Description of s2InstanceInS3", "$ref": "#/$defs/Structure2" },
                      "s1ListInS3": { "description": "Description of s1ListInS3", "type": "array", "items": { "$ref": "#/$defs/Structure1" } } } }
                  }
                }
                """;
        Map<String, Object> schema = OBJECT_MAPPER.readValue(schemaJson, new TypeReference<>() {});

        ActionDescriptor actionDescriptor = ActionDescriptor.builder()
                .name("org.deltafi.core.action.test.ReusedTypesAction")
                .type(ActionType.TRANSFORM)
                .schema(schema)
                .actionOptions(ActionOptions.builder().description("The description").build())
                .build();

        String docs = DocumentationGenerator.generateActionDocs(actionDescriptor);

        // the parameter table links the referenced type rather than showing a bare "object"
        Assertions.assertTrue(docs.contains("[`[Structure3]`](#structure3)"), "expected the table to link the list element type");

        // each named type is defined once in the Types section, linked by name from where it is used
        Assertions.assertEquals(1, countOccurrences(docs, "### Structure2\n"), "Structure2 should be defined once, not duplicated per use");
        Assertions.assertTrue(docs.contains("[`Structure2`](#structure2)"), "expected a by-name link to Structure2");
        Assertions.assertTrue(docs.contains("[`[Structure1]`](#structure1)"), "expected a by-name list link to Structure1");
        Assertions.assertTrue(docs.contains("`[string]`"), "expected the compact list type notation");
        // field descriptions are carried in the table's Description column
        Assertions.assertTrue(docs.contains("Description of s2StringList"), "expected the field description in the type table");
    }

    @Test
    void terminatesOnRecursiveTypes() throws IOException {
        // a self-referential type is defined once and references itself by name - no infinite expansion
        String schemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "root": { "description": "Tree root", "$ref": "#/$defs/Node" }
                  },
                  "$defs": {
                    "Node": { "type": "object", "properties": {
                      "name": { "type": "string", "description": "Node name" },
                      "children": { "description": "Child nodes", "type": "array", "items": { "$ref": "#/$defs/Node" } } } }
                  }
                }
                """;
        Map<String, Object> schema = OBJECT_MAPPER.readValue(schemaJson, new TypeReference<>() {});

        ActionDescriptor actionDescriptor = ActionDescriptor.builder()
                .name("org.deltafi.core.action.test.RecursiveAction")
                .type(ActionType.TRANSFORM)
                .schema(schema)
                .actionOptions(ActionOptions.builder().description("The description").build())
                .build();

        String docs = DocumentationGenerator.generateActionDocs(actionDescriptor);

        Assertions.assertEquals(1, countOccurrences(docs, "### Node\n"), "the recursive type should be defined exactly once");
        Assertions.assertTrue(docs.contains("[`[Node]`](#node)"), "expected the recursive child to link to Node instead of expanding forever");
    }

    @Test
    @SuppressWarnings({"deprecation", "removal"}) // verifies the migration shim for the deprecated description field
    void isBackwardCompatible() throws IOException {
        ActionDescriptor actionDescriptor = ActionDescriptor.builder()
                .name("TestAction")
                .description("The description")
                .schema(Collections.emptyMap())
                .build();

        String docs = DocumentationGenerator.generateActionDocs(actionDescriptor);

        Assertions.assertEquals(Resource.read("/documentation-generator/expectedDocsBackwardCompatible.md"), docs);
    }
}
