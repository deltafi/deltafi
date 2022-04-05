package org.deltafi.gradle.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import groovy.lang.Closure;
import org.deltafi.core.domain.generated.types.ActionDescriptor;
import org.deltafi.core.domain.generated.types.Plugin;
import org.deltafi.core.domain.generated.types.PluginCoordinates;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A Gradle plugin for building Deltafi plugins.
 * <p></p>
 * A generated plugin manifest named plugin.json will be written to the .plugin directory at the root of the project
 * upon finishing the build. The plugin manifest generation is configured by adding the following to the build.gradle
 * file:
 * <p></p>
 * <pre>
 * pluginManifest {
 *   description = "Provides conversions to/from STIX 1.X and 2.1 formats"
 *   // Dependencies may be specified as follows:
 *   //   dependencies = [{ group = "a"; artifact = "b"; version = "c" }, { group = "d"; artifact = "e"; version = "f" }]
 *   dependencies = []
 * }
 * </pre>
 */
public class PluginPlugin implements org.gradle.api.Plugin<Project> {
    public static class PluginManifestExtension {
        String description;
        List<Coordinates> dependencies = new ArrayList<>();

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
        PluginManifestExtension extension =
                project.getExtensions().create("pluginManifest", PluginManifestExtension.class);

        project.getGradle().buildFinished(buildResult -> generatePluginManifest(project, extension));
    }

    private void generatePluginManifest(Project project, PluginManifestExtension extension) {
        File pluginManifestDirectory = new File(project.getRootDir(), ".plugin");
        if (!pluginManifestDirectory.exists()) {
            try {
                Files.createDirectory(pluginManifestDirectory.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Map<Project, List<ActionDescriptor>> subProjectActionDescriptorsMap =
                buildSubprojectActionDescriptorsMap(project);

        List<PluginCoordinates> dependencies = new ArrayList<>();
        extension.dependencies.forEach(dependency -> dependencies.add(
                new PluginCoordinates(dependency.group, dependency.artifact, dependency.version)));

        Plugin plugin = Plugin.newBuilder()
                .pluginCoordinates(new PluginCoordinates(project.getGroup().toString(), project.getName(),
                        project.getVersion().toString()))
                .description(extension.description)
                .actions(subProjectActionDescriptorsMap.values().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()))
                .dependencies(dependencies)
                .build();

        try (FileWriter fileWriter = new FileWriter(new File(pluginManifestDirectory, "plugin.json"))) {
            fileWriter.write(OBJECT_MAPPER.writeValueAsString(plugin));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<Project, List<ActionDescriptor>> buildSubprojectActionDescriptorsMap(Project project) {
        return project.getSubprojects().stream()
                .collect(Collectors.toMap(Function.identity(), this::readActionDescriptors));
    }

    private List<ActionDescriptor> readActionDescriptors(Project subProject) {
        File actionsFile = new File(subProject.getBuildDir(), "actions.json");

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
}
