/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.Variable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * <h1>A Gradle plugin for validating Deltafi plugins.</h1>
 * <p>
 * This plugin should be added to the build.gradle file at the root of a project that is a Deltafi plugin.
 * It should be added to the plugins section as follows:
 * </p>
 * <pre>
 * plugins {
 *     id "org.deltafi.plugin" version "${deltafiVersion}"
 * }
 * </pre>
 * <p>
 * The plugin may be configured by adding the following:
 * </p>
 * <pre>
 * deltafiPlugin {
 *   flowsDir = "flows" (optional, defaults to "src/main/resources/flows")
 * }
 * </pre>
 */
public class PluginPlugin implements org.gradle.api.Plugin<Project> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static class DeltafiPluginExtension {
        String flowsDir;
    }

    public static class CheckDeltafiPluginTask extends DefaultTask {
        @Setter
        private PluginPlugin.DeltafiPluginExtension deltafiPluginExtension;

        @TaskAction
        public void check() {
            File flowsDirectory = new File(getProject().getProjectDir(), deltafiPluginExtension.flowsDir);
            if (!flowsDirectory.exists()) {
                getLogger().warn("Flows directory ({}) does not exist. No flows will be installed.", flowsDirectory);
            } else {
                checkVariables(flowsDirectory);
                checkFlowPlans(flowsDirectory);
            }
        }

        private void checkVariables(File flowsDirectory) {
            File variablesFile = new File(flowsDirectory, "variables.json");
            if (!variablesFile.exists()) {
                getLogger().info("No flow variables have been defined");
                return;
            }

            try (FileInputStream fis = new FileInputStream(variablesFile)) {
                @SuppressWarnings("unused")
                List<Variable> variables = OBJECT_MAPPER.readValue(fis, new TypeReference<>() {});
            } catch (IOException e) {
                throw new GradleException("Unable to load variables: " + e.getMessage(), e);
            }
        }

        private void checkFlowPlans(File flowsDirectory) {
            File[] flowFiles = flowsDirectory.listFiles(
                    file -> file.getName().endsWith(".json") && !file.getName().equals("variables.json"));

            if (flowFiles == null) {
                throw new GradleException(flowsDirectory + " is not a directory");
            }

            if (flowFiles.length == 0) {
                getLogger().warn("No flow plans exist in the flows directory ({})", flowsDirectory);
            }

            for (File flowFile : flowFiles) {
                try (FileInputStream fis = new FileInputStream(flowFile)) {
                    OBJECT_MAPPER.readValue(fis, FlowPlan.class);
                } catch (IOException e) {
                    throw new GradleException("Unable to load flow plan (" + flowFile + "): " + e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void apply(Project project) {
        DeltafiPluginExtension extension =
                project.getExtensions().create("deltafiPlugin", DeltafiPluginExtension.class);

        if (extension.flowsDir == null) {
            extension.flowsDir = "src/main/resources/flows";
        }

        project.getTasks().register("checkDeltafiPlugin", CheckDeltafiPluginTask.class,
                task -> task.setDeltafiPluginExtension(extension));
    }
}
