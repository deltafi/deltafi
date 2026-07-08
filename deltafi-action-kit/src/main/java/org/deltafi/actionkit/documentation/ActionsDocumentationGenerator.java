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
// ABOUTME: Generates markdown documentation for all actions in a plugin.
// ABOUTME: Runs as a Spring Boot command-line application, scanning for action beans and producing docs.
package org.deltafi.actionkit.documentation;

import com.github.victools.jsonschema.generator.SchemaGenerator;
import lombok.RequiredArgsConstructor;
import org.deltafi.actionkit.ActionKitAutoConfiguration;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.transform.Join;
import org.deltafi.actionkit.artifact.*;
import org.deltafi.actionkit.lookup.LookupTableClient;
import org.deltafi.common.action.documentation.DocumentationGenerator;
import org.deltafi.common.artifact.VersionChangedEvent;
import org.deltafi.common.cache.CacheAutoConfiguration;
import org.deltafi.common.content.ContentStorageServiceAutoConfiguration;
import org.deltafi.common.storage.s3.minio.MinioAutoConfiguration;
import org.deltafi.common.types.ActionDescriptor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Generates markdown documentation files for all actions discovered in a plugin package
 */
@RequiredArgsConstructor
@ComponentScan("${scan.base.package}")
@EnableAutoConfiguration(exclude = {ActionKitAutoConfiguration.class, CacheAutoConfiguration.class,
        ContentStorageServiceAutoConfiguration.class, MinioAutoConfiguration.class})
public class ActionsDocumentationGenerator implements CommandLineRunner {
    @Profile("docs")
    @Configuration
    public static class Config {
        @Bean
        public CacheManager cacheManager() {
            return new NoOpCacheManager();
        }

        @Bean
        public LookupTableClient lookupTableClient() {
            return new LookupTableClient("", null, null);
        }

        @Bean
        public ArtifactHandle artifactHandle() {
            return new ArtifactHandle() {
                @Override
                public String getArtifactName() {
                    return "";
                }

                @Override
                public String getVersion() {
                    return "";
                }

                @Override
                public String getDescription() {
                    return "";
                }

                @Override
                public String getContentType() {
                    return "";
                }

                @Override
                public byte[] getBytes() {
                    return new byte[0];
                }

                @Override
                public InputStream getInputStream() {
                    return null;
                }

                @Override
                public ArtifactManifest getManifest() {
                    return null;
                }

                @Override
                public Map<String, String> getLabels() {
                    return Map.of();
                }

                @Override
                public String getSha256() {
                    return "";
                }

                @Override
                public boolean isReady() {
                    return false;
                }

                @Override
                public boolean isDefault() {
                    return false;
                }

                @Override
                public String getSourcePrefix() {
                    return "";
                }

                @Override
                public boolean isShared() {
                    return false;
                }

                @Override
                public void refresh() {

                }

                @Override
                public ArtifactHandle onUpdate(Consumer<VersionChangedEvent> callback) {
                    return null;
                }
            };
        }

        @RequiredArgsConstructor
        public static class DoNothingArtifactFactory implements ArtifactFactory {
            private final ArtifactHandle artifactHandle;

            @Override
            public Builder artifact(String artifactName) {
                return new ArtifactFactory.Builder("", artifactName, (unused) -> artifactHandle);
            }

            @Override
            public ArtifactRegistryService registryService() {
                return null;
            }

            @Override
            public void start() {
            }

            @Override
            public void stop() {
            }
        }

        @Bean
        public ArtifactFactory artifactFactory(ArtifactHandle artifactHandle) {
            return new DoNothingArtifactFactory(artifactHandle);
        }

        @RequiredArgsConstructor
        public static class DoNothingArtifactRegistryService implements ArtifactRegistryService {
            private final ArtifactHandle artifactHandle;

            @Override
            public PublishArtifactResponse publishArtifact(String artifactName, ArtifactManifest manifest,
                    InputStream data) {
                return null;
            }

            @Override
            public PublishArtifactResponse publishArtifact(String artifactName, ArtifactManifest manifest,
                    InputStream data, boolean makeActive) {
                return null;
            }

            @Override
            public PublishArtifactResponse publishSharedArtifact(String artifactName, ArtifactManifest manifest,
                    InputStream data) {
                return null;
            }

            @Override
            public PublishArtifactResponse publishSharedArtifact(String artifactName, ArtifactManifest manifest,
                    InputStream data, boolean makeActive) {
                return null;
            }

            @Override
            public ArtifactHandle getArtifact(String artifactName) {
                return artifactHandle;
            }

            @Override
            public ArtifactHandle getArtifact(String artifactName, String alias) {
                return artifactHandle;
            }

            @Override
            public ArtifactHandle getSharedArtifact(String artifactName) {
                return artifactHandle;
            }

            @Override
            public ArtifactHandle getSharedArtifact(String artifactName, String alias) {
                return artifactHandle;
            }

            @Override
            public ArtifactStream streamArtifact(String artifactName) {
                return null;
            }

            @Override
            public ArtifactStream streamArtifact(String artifactName, String alias) {
                return null;
            }

            @Override
            public ArtifactStream streamSharedArtifact(String artifactName) {
                return null;
            }

            @Override
            public ArtifactStream streamSharedArtifact(String artifactName, String alias) {
                return null;
            }
        }

        @Bean
        public ArtifactRegistryService artifactRegistryService(ArtifactHandle artifactHandle) {
            return new DoNothingArtifactRegistryService(artifactHandle);
        }
    }

    private final List<Action<?, ?, ?>> actions;

    private final SchemaGenerator schemaGenerator = new ActionKitAutoConfiguration().parametersSchemaGenerator(null);

    /**
     * Entry point for the documentation generator.
     * @param args args[0] is the output directory, args[1] is the base package to scan
     */
    public static void main(String... args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("You must specify the output directory and the base package to scan");
        }

        new SpringApplicationBuilder(ActionsDocumentationGenerator.class, Config.class)
                .main(ActionsDocumentationGenerator.class)
                .properties(Map.of("scan.base.package", args[1], "spring.main.allow-bean-definition-overriding", true))
                .profiles("docs")
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

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build();

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
