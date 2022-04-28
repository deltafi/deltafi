package org.deltafi.gradle.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Closure;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.ActionDescriptor;
import org.deltafi.core.domain.generated.types.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A Gradle plugin for building Deltafi plugins.
 * <p></p>
 * This plugin should be added to the build.gradle file at the root of a multi-module project that is a Deltafi plugin.
 * It should be added to the plugins section as follows:
 * <p></p>
 * <pre>
 * plugins {
 *     id "org.deltafi.plugin" version "${deltafiVersion}"
 * }
 * </pre>
 * The plugin will generate files in the root project's "charts" directory for automatically registering the plugin when
 * it's installed by Helm. The plugin registration is configured by adding the following:
 * <p></p>
 * <pre>
 * deltafiPlugin {
 *   displayName = "Deltafi STIX"
 *   description = "Provides conversions to/from STIX 1.X and 2.1 formats"
 *   actionKitVersion = "${deltafiVersion}"
 *   // Dependencies on other Deltafi plugins may be specified as follows:
 *   //   dependencies = [{ group = "a"; artifact = "b"; version = "c" }, { group = "d"; artifact = "e"; version = "f" }]
 *   dependencies = []
 * }
 * </pre>
 */
public class PluginPlugin implements org.gradle.api.Plugin<Project> {
    public static class DeltafiPluginExtension {
        String displayName;
        String description;
        String actionKitVersion;
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
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    @Override
    public void apply(Project project) {
        DeltafiPluginExtension extension =
                project.getExtensions().create("deltafiPlugin", DeltafiPluginExtension.class);

        project.getGradle().buildFinished(buildResult -> installHelmFiles(project, extension));
    }

    private void installHelmFiles(Project project, DeltafiPluginExtension extension) {
        installRegisterJob(project);
        installPluginManifest(project, extension);
    }

    private void installRegisterJob(Project project) {
        File chartsTemplatesDirectory = new File(project.getRootDir(), "charts/templates");
        if (!chartsTemplatesDirectory.exists() && !chartsTemplatesDirectory.mkdirs()) {
            System.out.println("Unable to create charts/templates directory!");
        }

        InputStream registerJobInputStream = getClass().getResourceAsStream("/job-register-plugin.yaml");
        try {
            Files.copy(registerJobInputStream, new File(chartsTemplatesDirectory, "job-register-plugin.yaml").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.out.println("Unable to copy job to charts/templates directory!");
        }
    }

    private void installPluginManifest(Project project, DeltafiPluginExtension extension) {
        File chartsFilesDirectory = new File(project.getRootDir(), "charts/files");
        if (!chartsFilesDirectory.exists() && !chartsFilesDirectory.mkdirs()) {
            System.out.println("Unable to create charts/files directory!");
        }

        Map<Project, List<ActionDescriptor>> subProjectActionDescriptorsMap =
                buildSubprojectActionDescriptorsMap(project);

        List<PluginCoordinates> dependencies = new ArrayList<>();
        extension.dependencies.forEach(dependency -> dependencies.add(
                new PluginCoordinates(dependency.group, dependency.artifact, dependency.version)));

        Plugin plugin = Plugin.newBuilder()
                .pluginCoordinates(new PluginCoordinates(project.getGroup().toString(), project.getName(),
                        project.getVersion().toString()))
                .displayName(extension.displayName)
                .description(extension.description)
                .actionKitVersion(extension.actionKitVersion)
                .actions(subProjectActionDescriptorsMap.values().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()))
                .dependencies(dependencies)
                .build();

        try (FileWriter fileWriter = new FileWriter(new File(chartsFilesDirectory, "plugin.json"))) {
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
