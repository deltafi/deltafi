/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.gradle.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import groovy.lang.Closure;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A Gradle plugin for building Deltafi plugins.
 * <p></p>
 * This plugin should be added to the build.gradle file at the root of a project that is a Deltafi plugin.
 * It should be added to the plugins section as follows:
 * <p></p>
 * <pre>
 * plugins {
 *     id "org.deltafi.plugin" version "${deltafiVersion}"
 * }
 * </pre>
 * The plugin registration is configured by adding the following:
 * <p></p>
 * <pre>
 * deltafiPlugin {
 *   displayName = "Deltafi STIX"
 *   description = "Provides conversions to/from STIX 1.X and 2.1 formats"
 *   // Dependencies on other Deltafi plugins may be specified as follows:
 *   //   dependencies = [{ group = "a"; artifact = "b"; version = "c" }, { group = "d"; artifact = "e"; version = "f" }]
 *   dependencies = []
 *   helmDir = "build/helm" (optional, defaults to "rootDir/charts")
 *   manifestDir = "build/plugin" (optional, defaults to "helmDir/files")
 * }
 * </pre>
 */
public class PluginPlugin implements org.gradle.api.Plugin<Project> {
    public static class DeltafiPluginExtension {
        String displayName;
        String description;
        List<Coordinates> dependencies = new ArrayList<>();
        String helmDir;
        String manifestDir;

        public void setDependencies(List<Closure<?>> dependencyClosures) {
            dependencyClosures.forEach(dependencyClosure -> {
                dependencyClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
                Coordinates coordinates = new Coordinates();
                dependencyClosure.setDelegate(coordinates);
                dependencyClosure.run();
                dependencies.add(coordinates);
            });
        }
    }

    public static class Coordinates {
        String group;
        String artifact;
        String version;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public void apply(Project project) {
        DeltafiPluginExtension extension =
                project.getExtensions().create("deltafiPlugin", DeltafiPluginExtension.class);

        if (extension.helmDir == null) {
            extension.helmDir = project.getRootDir() + "/charts";
        }
        if (extension.manifestDir == null) {
            extension.manifestDir = extension.helmDir + "/files";
        }

        project.getGradle().buildFinished(buildResult -> generateFiles(project, extension));
    }

    private void generateFiles(Project project, DeltafiPluginExtension extension) {
        generatePluginManifest(project, extension);
        generateHelmFiles(project, extension);
    }

    private void generatePluginManifest(Project project, DeltafiPluginExtension extension) {
        File manifestDirectory = new File(extension.manifestDir);
        if (!manifestDirectory.exists() && !manifestDirectory.mkdirs()) {
            System.out.println("Unable to create manifest directory: " + manifestDirectory);
            return;
        }

        Map<Project, List<ActionDescriptor>> projectActionDescriptorsMap = new HashMap<>();
        projectActionDescriptorsMap.put(project, readActionDescriptors(project));
        projectActionDescriptorsMap.putAll(buildSubprojectActionDescriptorsMap(project));

        List<PluginCoordinates> dependencies = new ArrayList<>();
        extension.dependencies.forEach(dependency -> dependencies.add(
                new PluginCoordinates(dependency.group, dependency.artifact, dependency.version)));

        String version = null;
        try {
            Properties properties = new Properties();
            properties.load(getClass().getResourceAsStream("/plugin.properties"));
            version = properties.getProperty("version");
        } catch (IOException e) {
            System.out.println("Unable to load plugin.properties: " + e.getMessage());
            return;
        }

        Plugin plugin = Plugin.newBuilder()
                .pluginCoordinates(new PluginCoordinates(project.getGroup().toString(), project.getName(),
                        project.getVersion().toString()))
                .displayName(extension.displayName)
                .description(extension.description)
                .actionKitVersion(version)
                .actions(projectActionDescriptorsMap.values().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()))
                .dependencies(dependencies)
                .build();

        try (FileWriter fileWriter = new FileWriter(new File(manifestDirectory, "plugin.json"))) {
            fileWriter.write(OBJECT_MAPPER.writeValueAsString(plugin));
        } catch (IOException e) {
            System.out.println("Unable to write plugin.json: " + e.getMessage());
        }
    }

    private Map<Project, List<ActionDescriptor>> buildSubprojectActionDescriptorsMap(Project project) {
        return project.getSubprojects().stream()
                .collect(Collectors.toMap(Function.identity(), this::readActionDescriptors));
    }

    private List<ActionDescriptor> readActionDescriptors(Project project) {
        File actionsFile = new File(project.getBuildDir(), "actions.json");

        if (!actionsFile.exists()) {
            return List.of();
        }

        try {
            return OBJECT_MAPPER.readValue(actionsFile, new TypeReference<>() {});
        } catch (IOException e) {
            System.out.println("actions.json file couldn't be read: " + e.getMessage());
            return List.of();
        }
    }

    private void generateHelmFiles(Project project, DeltafiPluginExtension extension) {
        File helmDirectory = new File(extension.helmDir);
        File helmTemplatesDirectory = new File(helmDirectory, "templates");
        if (!helmTemplatesDirectory.exists() && !helmTemplatesDirectory.mkdirs()) {
            System.out.println("Unable to create helm templates directory: " + helmTemplatesDirectory);
            return;
        }

        InputStream registerJobInputStream = getClass().getResourceAsStream("/job-register-plugin.yaml");
        try {
            Files.copy(registerJobInputStream, new File(helmTemplatesDirectory, "job-register-plugin.yaml").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.out.println("Unable to copy register plugin job to helm templates directory: " + e.getMessage());
        }
    }
}
