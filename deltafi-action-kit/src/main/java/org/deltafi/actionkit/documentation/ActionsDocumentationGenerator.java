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
package org.deltafi.actionkit.documentation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import lombok.RequiredArgsConstructor;
import org.deltafi.actionkit.ActionKitAutoConfiguration;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.transform.Join;
import org.deltafi.common.action.documentation.DocumentationGenerator;
import org.deltafi.common.cache.CacheAutoConfiguration;
import org.deltafi.common.content.ContentStorageServiceAutoConfiguration;
import org.deltafi.common.storage.s3.minio.MinioAutoConfiguration;
import org.deltafi.common.types.ActionDescriptor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@ComponentScan("${scan.base.package}")
@EnableAutoConfiguration(exclude = {ActionKitAutoConfiguration.class, CacheAutoConfiguration.class,
        ContentStorageServiceAutoConfiguration.class, DataSourceAutoConfiguration.class, MinioAutoConfiguration.class})
public class ActionsDocumentationGenerator implements CommandLineRunner {
    private final List<Action<?, ?, ?>> actions;

    private final SchemaGenerator schemaGenerator = new ActionKitAutoConfiguration().parametersSchemaGenerator(null);

    public static void main(String... args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("You must specify the output directory and the base package to scan");
        }

        new SpringApplicationBuilder(ActionsDocumentationGenerator.class)
                .main(ActionsDocumentationGenerator.class)
                .properties("scan.base.package=" + args[1])
                .run(args);
    }

    @Override
    public void run(String... args) throws Exception {
        File directory = new File(args[0]);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        for (Action<?, ?, ?> action : actions) {
            String actionDocs = DocumentationGenerator.generateActionDocs((buildActionDescriptor(action)));
            if (actionDocs == null) {
                continue;
            }
            File markdownFile = new File(directory, action.getClassCanonicalName() + ".md");
            try (FileWriter fileWriter = new FileWriter(markdownFile)) {
                fileWriter.write(actionDocs);
            }
        }
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    private ActionDescriptor buildActionDescriptor(Action<?, ?, ?> action) {
        Map<String, Object> schema = OBJECT_MAPPER.convertValue(
                schemaGenerator.generateSchema(action.getParamClass()), new TypeReference<>() {});

        return ActionDescriptor.builder()
                .name(action.getClassCanonicalName())
                .type(action.getActionType())
                .supportsJoin(action instanceof Join)
                .schema(schema)
                .actionOptions(action.getActionOptions())
                .build();
    }
}
