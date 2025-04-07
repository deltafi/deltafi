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
